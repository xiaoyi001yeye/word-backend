package com.example.words.service;

import com.example.words.exception.BadRequestException;
import com.example.words.exception.BadGatewayException;
import com.example.words.model.VideoStorageConfig;
import com.qcloud.vod.VodUploadClient;
import com.qcloud.vod.exception.VodClientException;
import com.qcloud.vod.model.VodUploadRequest;
import com.qcloud.vod.model.VodUploadResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.DeleteMediaRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosResponse;
import com.tencentcloudapi.vod.v20180717.models.DescribeSubAppIdsRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeSubAppIdsResponse;
import com.tencentcloudapi.vod.v20180717.models.MediaProcessTaskInput;
import com.tencentcloudapi.vod.v20180717.models.MediaInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaBasicInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaTranscodeInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaTranscodeItem;
import com.tencentcloudapi.vod.v20180717.models.ProcessMediaRequest;
import com.tencentcloudapi.vod.v20180717.models.TranscodeTaskInput;
import com.tencentcloudapi.vod.v20180717.models.SubAppIdInfo;
import java.util.Arrays;
import java.util.Comparator;
import org.springframework.stereotype.Service;

@Service
public class TencentVodSdkGateway implements TencentVodGateway {

    private static final long PRESET_360P_MP4_DEFINITION = 100010L;

    private final VideoStorageConfigCryptoService cryptoService;

    public TencentVodSdkGateway(VideoStorageConfigCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public TencentVodUploadResult upload(
            VideoStorageConfig config,
            java.nio.file.Path filePath,
            String originalFileName,
            String title,
            String description) {
        try {
            VodUploadClient client = new VodUploadClient(
                    cryptoService.decrypt(config.getSecretIdEncrypted()),
                    cryptoService.decrypt(config.getSecretKeyEncrypted())
            );
            VodUploadRequest request = new VodUploadRequest();
            request.setMediaFilePath(filePath.toString());
            request.setMediaName(title);
            request.setMediaType(resolveMediaType(originalFileName));
            if (config.getSubAppId() != null) {
                request.setSubAppId(config.getSubAppId());
            }
            if (config.getProcedureName() != null && !config.getProcedureName().isBlank()) {
                request.setProcedure(config.getProcedureName());
            }

            VodUploadResponse response = client.upload(config.getRegion(), request);
            boolean transcodeRequested = startDefault360pTranscodeIfNeeded(config, response.getFileId());
            return new TencentVodUploadResult(
                    response.getFileId(),
                    response.getMediaUrl(),
                    response.getCoverUrl(),
                    transcodeRequested
            );
        } catch (VodClientException ex) {
            throw new BadGatewayException("Tencent VOD upload failed: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BadGatewayException("Tencent VOD upload failed: " + ex.getMessage());
        }
    }

    @Override
    public TencentVodMediaInfo describeMedia(VideoStorageConfig config, String fileId) {
        try {
            DescribeMediaInfosRequest request = new DescribeMediaInfosRequest();
            request.setFileIds(new String[]{fileId});
            request.setFilters(new String[]{"basicInfo", "metaData", "transcodeInfo"});
            if (config.getSubAppId() != null) {
                request.setSubAppId(config.getSubAppId());
            }

            DescribeMediaInfosResponse response = buildVodClient(config).DescribeMediaInfos(request);
            MediaInfo[] mediaInfoSet = response.getMediaInfoSet();
            if (mediaInfoSet == null || mediaInfoSet.length == 0) {
                return new TencentVodMediaInfo(fileId, null, null, null, false, false);
            }

            MediaInfo mediaInfo = mediaInfoSet[0];
            MediaBasicInfo basicInfo = mediaInfo.getBasicInfo();
            Long durationSeconds = null;
            if (mediaInfo.getMetaData() != null && mediaInfo.getMetaData().getDuration() != null) {
                durationSeconds = Long.valueOf(Math.round(mediaInfo.getMetaData().getDuration()));
            }

            String preferredTranscodeUrl = resolvePreferredTranscodeUrl(mediaInfo.getTranscodeInfo());
            String mediaUrl = preferredTranscodeUrl != null
                    ? preferredTranscodeUrl
                    : basicInfo == null ? null : basicInfo.getMediaUrl();
            String coverUrl = basicInfo == null ? null : basicInfo.getCoverUrl();
            return new TencentVodMediaInfo(
                    fileId,
                    mediaUrl,
                    coverUrl,
                    durationSeconds,
                    mediaUrl != null && !mediaUrl.isBlank(),
                    preferredTranscodeUrl != null
            );
        } catch (TencentCloudSDKException ex) {
            throw new BadGatewayException("Tencent VOD media query failed: " + ex.getMessage());
        }
    }

    @Override
    public void deleteMedia(VideoStorageConfig config, String fileId) {
        try {
            DeleteMediaRequest request = new DeleteMediaRequest();
            request.setFileId(fileId);
            if (config.getSubAppId() != null) {
                request.setSubAppId(config.getSubAppId());
            }
            buildVodClient(config).DeleteMedia(request);
        } catch (TencentCloudSDKException ex) {
            String errorText = ex.toString();
            if (errorText != null && errorText.contains("NotExist")) {
                return;
            }
            throw new BadGatewayException("Tencent VOD delete failed: " + ex.getMessage());
        }
    }

    @Override
    public void validate(VideoStorageConfig config) {
        ensureCredentialLooksReady(config);
        try {
            DescribeSubAppIdsResponse response = buildVodClient(config).DescribeSubAppIds(new DescribeSubAppIdsRequest());
            verifyConfiguredSubApp(response, config.getSubAppId());
        } catch (TencentCloudSDKException ex) {
            throw new BadGatewayException(buildValidationErrorMessage(ex));
        }
    }

    private VodClient buildVodClient(VideoStorageConfig config) throws TencentCloudSDKException {
        Credential credential = new Credential(
                cryptoService.decrypt(config.getSecretIdEncrypted()),
                cryptoService.decrypt(config.getSecretKeyEncrypted())
        );
        return new VodClient(credential, config.getRegion());
    }

    private boolean startDefault360pTranscodeIfNeeded(VideoStorageConfig config, String fileId)
            throws TencentCloudSDKException {
        if (config.getProcedureName() != null && !config.getProcedureName().isBlank()) {
            return false;
        }

        ProcessMediaRequest request = new ProcessMediaRequest();
        request.setFileId(fileId);
        if (config.getSubAppId() != null) {
            request.setSubAppId(config.getSubAppId());
        }
        request.setTasksNotifyMode("Finish");

        TranscodeTaskInput transcodeTaskInput = new TranscodeTaskInput();
        transcodeTaskInput.setDefinition(PRESET_360P_MP4_DEFINITION);
        MediaProcessTaskInput mediaProcessTaskInput = new MediaProcessTaskInput();
        mediaProcessTaskInput.setTranscodeTaskSet(new TranscodeTaskInput[]{transcodeTaskInput});
        request.setMediaProcessTask(mediaProcessTaskInput);

        buildVodClient(config).ProcessMedia(request);
        return true;
    }

    private void ensureCredentialLooksReady(VideoStorageConfig config) {
        String secretId = cryptoService.decrypt(config.getSecretIdEncrypted()).trim();
        String secretKey = cryptoService.decrypt(config.getSecretKeyEncrypted()).trim();

        if (secretId.length() < 8 || secretKey.length() < 8) {
            throw new BadRequestException(
                    "Current video storage config still uses placeholder SecretId/SecretKey. "
                            + "Please save real Tencent Cloud API credentials before testing"
            );
        }
    }

    private void verifyConfiguredSubApp(DescribeSubAppIdsResponse response, Long configuredSubAppId) {
        if (configuredSubAppId == null) {
            return;
        }

        SubAppIdInfo[] subAppInfos = response.getSubAppIdInfoSet();
        boolean found = subAppInfos != null
                && Arrays.stream(subAppInfos)
                .anyMatch(info -> info != null && configuredSubAppId.equals(info.getSubAppId()));
        if (!found) {
            throw new BadRequestException(
                    "Configured SubAppId " + configuredSubAppId
                            + " was not found in the current Tencent VOD account"
            );
        }
    }

    private String buildValidationErrorMessage(TencentCloudSDKException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Tencent VOD validation failed";
        }
        if (message.contains("AuthFailure.SecretIdNotFound")) {
            return "Tencent VOD validation failed: SecretId was not found. "
                    + "Please check whether the saved SecretId is a real Tencent Cloud API key";
        }
        if (message.contains("AuthFailure.SignatureFailure")) {
            return "Tencent VOD validation failed: SecretKey does not match the SecretId";
        }
        if (message.contains("AuthFailure.TokenFailure")) {
            return "Tencent VOD validation failed: Tencent Cloud credential token is invalid";
        }
        return "Tencent VOD validation failed: " + message;
    }

    private String resolvePreferredTranscodeUrl(MediaTranscodeInfo transcodeInfo) {
        if (transcodeInfo == null || transcodeInfo.getTranscodeSet() == null) {
            return null;
        }

        return Arrays.stream(transcodeInfo.getTranscodeSet())
                .filter(item -> item != null
                        && item.getDefinition() != null
                        && item.getDefinition() != 0L
                        && item.getUrl() != null
                        && !item.getUrl().isBlank())
                .sorted(Comparator
                        .comparing((MediaTranscodeItem item) -> item.getDefinition() == PRESET_360P_MP4_DEFINITION ? 0 : 1)
                        .thenComparing(item -> item.getHeight() == null ? Long.MAX_VALUE : item.getHeight())
                        .thenComparing(item -> item.getBitrate() == null ? Long.MAX_VALUE : item.getBitrate()))
                .map(MediaTranscodeItem::getUrl)
                .findFirst()
                .orElse(null);
    }

    private String resolveMediaType(String originalFileName) {
        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == originalFileName.length() - 1) {
            return null;
        }
        return originalFileName.substring(extensionIndex + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
