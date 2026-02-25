import { useState } from 'react';
import { dictionaryWordApi } from '../api';
import type { Dictionary } from '../types';
import type { WordListProcessResult } from '../api';

interface CsvImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  dictionary: Dictionary;
  onSuccess?: () => void;
}

export function CsvImportModal({ isOpen, onClose, dictionary, onSuccess }: CsvImportModalProps) {
  const [file, setFile] = useState<File | null>(null);
  const [hasHeader, setHasHeader] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<WordListProcessResult | null>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFile = e.target.files?.[0];
    if (selectedFile) {
      // 验证文件类型和大小
      if (!selectedFile.name.endsWith('.csv')) {
        setError('请选择CSV格式的文件');
        return;
      }
      if (selectedFile.size > 10 * 1024 * 1024) { // 10MB限制
        setError('文件大小不能超过10MB');
        return;
      }
      setFile(selectedFile);
      setError(null);
      setResult(null);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setError('请选择要上传的文件');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('hasHeader', hasHeader.toString());

      const response = await dictionaryWordApi.importCsv(dictionary.id, formData);
      setResult(response);

      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setFile(null);
    setError(null);
    setResult(null);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <h2 className="modal__title">导入CSV文件到辞书</h2>
          <button className="modal__close" onClick={handleClose}>&times;</button>
        </div>
        
        <form onSubmit={handleSubmit} className="modal__form">
          {/* 文件选择区域 */}
          <div className="form__group">
            <label className="form__label">选择CSV文件 *</label>
            <div className="file-upload-area">
              <input
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                className="file-input"
                disabled={loading}
              />
              {file && (
                <div className="file-info">
                  <span>{file.name}</span>
                  <span>({(file.size / 1024).toFixed(1)} KB)</span>
                </div>
              )}
            </div>
          </div>

          {/* 选项设置 */}
          <div className="form__group">
            <label className="form__checkbox">
              <input
                type="checkbox"
                checked={hasHeader}
                onChange={(e) => setHasHeader(e.target.checked)}
                disabled={loading}
              />
              文件包含表头行
            </label>
            <div className="form__hint">
              如果CSV文件第一行是列名，请勾选此项
            </div>
          </div>

          {/* 格式说明 */}
          <div className="form__group">
            <div className="form__hint">
              <h4>CSV格式要求：</h4>
              <ul>
                <li>编码：UTF-8</li>
                <li>分隔符：逗号(,)</li>
                <li>必需列：单词, 定义</li>
                <li>可选列：音标, 词性, 例句, 翻译, 难度</li>
                <li>示例行：apple,/ˈæpəl/,n. 苹果,名词,I ate an apple.,我吃了一个苹果,2</li>
              </ul>
            </div>
          </div>

          {error && (
            <div className="form__error">
              {error}
            </div>
          )}

          {result && (
            <div className="form__result">
              <h3 className="form__result-title">导入结果</h3>
              <div className="form__result-stats">
                <div className="form__result-stat">
                  <span className="form__result-label">总单词数：</span>
                  <span className="form__result-value">{result.total}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">已存在：</span>
                  <span className="form__result-value">{result.existed}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">新创建：</span>
                  <span className="form__result-value">{result.created}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">成功添加：</span>
                  <span className="form__result-value">{result.added}</span>
                </div>
                <div className="form__result-stat">
                  <span className="form__result-label">失败：</span>
                  <span className="form__result-value">{result.failed}</span>
                </div>
              </div>
            </div>
          )}

          <div className="modal__footer">
            <button
              type="button"
              className="btn btn--secondary"
              onClick={handleClose}
              disabled={loading}
            >
              取消
            </button>
            <button
              type="submit"
              className="btn btn--primary"
              disabled={loading || !file}
            >
              {loading ? '上传中...' : '开始导入'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}