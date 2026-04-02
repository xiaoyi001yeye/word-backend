import { useCallback, useEffect, useMemo, useState } from 'react';
import { aiApi, aiConfigApi } from '../api';
import type {
  AiChatResponse,
  AiConfig,
  AiConfigStatus,
  CreateAiConfigPayload,
  UpdateAiConfigPayload,
} from '../types';

interface AiConfigManagementModalProps {
  isOpen: boolean;
  actorRole: 'TEACHER' | 'STUDENT';
  onClose: () => void;
}

interface AiConfigFormState {
  providerName: string;
  apiUrl: string;
  apiKey: string;
  modelName: string;
  status: AiConfigStatus;
  isDefault: boolean;
  remark: string;
}

interface ChatExchange {
  userMessage: string;
  reply: string;
}

const EMPTY_FORM: AiConfigFormState = {
  providerName: '',
  apiUrl: '',
  apiKey: '',
  modelName: '',
  status: 'DISABLED',
  isDefault: false,
  remark: '',
};

function toFormState(config?: AiConfig | null): AiConfigFormState {
  if (!config) {
    return EMPTY_FORM;
  }

  return {
    providerName: config.providerName,
    apiUrl: config.apiUrl,
    apiKey: '',
    modelName: config.modelName,
    status: config.status,
    isDefault: config.isDefault,
    remark: config.remark ?? '',
  };
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return '暂无';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function AiConfigManagementModal({
  isOpen,
  actorRole,
  onClose,
}: AiConfigManagementModalProps) {
  const [configs, setConfigs] = useState<AiConfig[]>([]);
  const [selectedConfigId, setSelectedConfigId] = useState<number | null>(null);
  const [form, setForm] = useState<AiConfigFormState>(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [actionLoadingKey, setActionLoadingKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [chatInput, setChatInput] = useState('你好，请回复 test-ok。');
  const [chatSending, setChatSending] = useState(false);
  const [chatHistory, setChatHistory] = useState<ChatExchange[]>([]);

  const selectedConfig = useMemo(
    () => configs.find((config) => config.id === selectedConfigId) ?? null,
    [configs, selectedConfigId],
  );

  const resetEditor = useCallback((config?: AiConfig | null) => {
    setForm(toFormState(config));
    setChatHistory([]);
    setChatInput('你好，请回复 test-ok。');
  }, []);

  const loadConfigs = useCallback(async (preferredConfigId?: number | null) => {
    setLoading(true);
    setError(null);

    try {
      const nextConfigs = await aiConfigApi.list();
      setConfigs(nextConfigs);

      const nextSelected = preferredConfigId !== undefined
        ? nextConfigs.find((item) => item.id === preferredConfigId) ?? null
        : nextConfigs.find((item) => item.id === selectedConfigId) ?? nextConfigs[0] ?? null;

      setSelectedConfigId(nextSelected?.id ?? null);
      resetEditor(nextSelected);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载 AI 配置失败');
    } finally {
      setLoading(false);
    }
  }, [resetEditor, selectedConfigId]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setSuccessMessage(null);
    void loadConfigs();
  }, [isOpen, loadConfigs]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    resetEditor(selectedConfig);
  }, [isOpen, resetEditor, selectedConfig]);

  if (!isOpen) {
    return null;
  }

  const isEditing = selectedConfig !== null;

  const updateForm = <K extends keyof AiConfigFormState>(key: K, value: AiConfigFormState[K]) => {
    setForm((current) => ({
      ...current,
      [key]: value,
    }));
  };

  const handleCreateMode = () => {
    setSelectedConfigId(null);
    setSuccessMessage(null);
    setError(null);
    resetEditor(null);
  };

  const buildPayload = (): CreateAiConfigPayload | UpdateAiConfigPayload => ({
    providerName: form.providerName.trim(),
    apiUrl: form.apiUrl.trim(),
    apiKey: form.apiKey,
    modelName: form.modelName.trim(),
    status: form.status,
    isDefault: form.isDefault,
    remark: form.remark.trim() || undefined,
  });

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setSuccessMessage(null);

    try {
      if (!form.providerName.trim()) {
        throw new Error('请输入 AI 厂商名称');
      }
      if (!form.apiUrl.trim()) {
        throw new Error('请输入接口地址');
      }
      if (!form.modelName.trim()) {
        throw new Error('请输入模型名称');
      }
      if (!isEditing && !form.apiKey.trim()) {
        throw new Error('新建配置时必须填写 API Key');
      }
      if (form.isDefault && form.status !== 'ENABLED') {
        throw new Error('默认配置必须处于启用状态');
      }

      const payload = buildPayload();
      const saved = isEditing
        ? await aiConfigApi.update(selectedConfig.id, payload)
        : await aiConfigApi.create(payload as CreateAiConfigPayload);

      setSuccessMessage(isEditing ? 'AI 配置已更新。' : 'AI 配置已创建。');
      await loadConfigs(saved.id);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '保存 AI 配置失败');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleStatus = async (config: AiConfig) => {
    setActionLoadingKey(`status-${config.id}`);
    setError(null);
    setSuccessMessage(null);

    try {
      const nextStatus: AiConfigStatus = config.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
      await aiConfigApi.updateStatus(config.id, nextStatus);
      setSuccessMessage(`${config.providerName} 已切换为 ${nextStatus === 'ENABLED' ? '启用' : '禁用'} 状态。`);
      await loadConfigs(config.id);
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : '更新配置状态失败');
    } finally {
      setActionLoadingKey(null);
    }
  };

  const handleSetDefault = async (config: AiConfig) => {
    setActionLoadingKey(`default-${config.id}`);
    setError(null);
    setSuccessMessage(null);

    try {
      await aiConfigApi.setDefault(config.id);
      setSuccessMessage(`${config.providerName} 已设为默认配置。`);
      await loadConfigs(config.id);
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : '设置默认配置失败');
    } finally {
      setActionLoadingKey(null);
    }
  };

  const handleDelete = async (config: AiConfig) => {
    if (!window.confirm(`确定删除配置「${config.providerName} / ${config.modelName}」吗？`)) {
      return;
    }

    setActionLoadingKey(`delete-${config.id}`);
    setError(null);
    setSuccessMessage(null);

    try {
      await aiConfigApi.remove(config.id);
      setSuccessMessage('AI 配置已删除。');
      await loadConfigs(config.id === selectedConfigId ? null : selectedConfigId);
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : '删除配置失败');
    } finally {
      setActionLoadingKey(null);
    }
  };

  const handleQuickTest = async (config: AiConfig) => {
    setActionLoadingKey(`test-${config.id}`);
    setError(null);
    setSuccessMessage(null);

    try {
      const result = await aiConfigApi.test(config.id);
      setSuccessMessage(`测试成功，模型回复：${result.reply}`);
      if (config.id === selectedConfigId) {
        setChatHistory((current) => [
          ...current,
          { userMessage: 'Reply with test-ok only.', reply: result.reply },
        ]);
      }
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : '测试连接失败');
    } finally {
      setActionLoadingKey(null);
    }
  };

  const handleSendChat = async () => {
    if (!selectedConfig) {
      setError('请先选择一条配置');
      return;
    }
    if (selectedConfig.status !== 'ENABLED') {
      setError('请先启用当前配置，再进行对话测试');
      return;
    }
    if (!chatInput.trim()) {
      setError('请输入测试消息');
      return;
    }

    setChatSending(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const response: AiChatResponse = await aiApi.chat({
        configId: selectedConfig.id,
        messages: [{ role: 'user', content: chatInput.trim() }],
      });

      setChatHistory((current) => [
        ...current,
        {
          userMessage: chatInput.trim(),
          reply: response.reply,
        },
      ]);
      setChatInput('');
      setSuccessMessage('对话测试成功，当前配置可用。');
    } catch (chatError) {
      setError(chatError instanceof Error ? chatError.message : '发送测试消息失败');
    } finally {
      setChatSending(false);
    }
  };

  return (
    <div className="modal-overlay">
      <div className="modal modal--wide ai-config">
        <div className="modal__header">
          <div>
            <h2 className="modal__title">AI 配置</h2>
            <p className="ai-config__subtitle">
              当前角色为 {actorRole === 'TEACHER' ? '教师' : '学生'}，这里只管理你自己的 AI 凭证与模型配置。
            </p>
          </div>
          <button className="modal__close" onClick={onClose} disabled={saving || chatSending}>
            &times;
          </button>
        </div>

        <div className="modal__form ai-config__layout">
          <section className="ai-config__column ai-config__column--list">
            <div className="ai-config__section-header">
              <div>
                <p className="panel__eyebrow">Configs</p>
                <h3 className="ai-config__section-title">我的配置</h3>
              </div>
              <button className="btn btn--secondary" type="button" onClick={handleCreateMode} disabled={saving}>
                新建配置
              </button>
            </div>

            {error && <div className="form__error">{error}</div>}
            {successMessage && <div className="form__success">{successMessage}</div>}

            <div className="ai-config__list">
              {loading ? (
                <div className="ai-config__empty">正在加载配置...</div>
              ) : configs.length === 0 ? (
                <div className="ai-config__empty">你还没有保存任何 AI 配置。</div>
              ) : (
                configs.map((config) => (
                  <button
                    key={config.id}
                    type="button"
                    className={`ai-config__item ${config.id === selectedConfigId ? 'ai-config__item--active' : ''}`}
                    onClick={() => setSelectedConfigId(config.id)}
                  >
                    <div className="ai-config__item-head">
                      <strong>{config.providerName}</strong>
                      <div className="ai-config__badges">
                        <span className={`ai-config__status ai-config__status--${config.status.toLowerCase()}`}>
                          {config.status === 'ENABLED' ? '启用' : '禁用'}
                        </span>
                        {config.isDefault && <span className="ai-config__status ai-config__status--default">默认</span>}
                      </div>
                    </div>
                    <p className="ai-config__meta">{config.modelName}</p>
                    <p className="ai-config__meta">{config.apiKeyMasked}</p>
                    <p className="ai-config__meta">更新于 {formatDateTime(config.updatedAt)}</p>
                    <div className="ai-config__actions">
                      <span className="btn btn--secondary">编辑</span>
                      <span
                        className="btn btn--secondary"
                        onClick={(event) => {
                          event.stopPropagation();
                          void handleToggleStatus(config);
                        }}
                      >
                        {actionLoadingKey === `status-${config.id}`
                          ? '处理中...'
                          : config.status === 'ENABLED'
                            ? '禁用'
                            : '启用'}
                      </span>
                      <span
                        className="btn btn--secondary"
                        onClick={(event) => {
                          event.stopPropagation();
                          void handleSetDefault(config);
                        }}
                      >
                        {actionLoadingKey === `default-${config.id}` ? '处理中...' : '设为默认'}
                      </span>
                      <span
                        className="btn btn--secondary"
                        onClick={(event) => {
                          event.stopPropagation();
                          void handleQuickTest(config);
                        }}
                      >
                        {actionLoadingKey === `test-${config.id}` ? '测试中...' : '测试'}
                      </span>
                      <span
                        className="btn btn--secondary ai-config__danger"
                        onClick={(event) => {
                          event.stopPropagation();
                          void handleDelete(config);
                        }}
                      >
                        {actionLoadingKey === `delete-${config.id}` ? '删除中...' : '删除'}
                      </span>
                    </div>
                  </button>
                ))
              )}
            </div>
          </section>

          <section className="ai-config__column">
            <div className="ai-config__section-header">
              <div>
                <p className="panel__eyebrow">Editor</p>
                <h3 className="ai-config__section-title">{isEditing ? '编辑配置' : '创建配置'}</h3>
              </div>
              {selectedConfig && (
                <span className="app__masthead-chip">
                  {selectedConfig.status === 'ENABLED' ? '当前可调用' : '当前未启用'}
                </span>
              )}
            </div>

            <form onSubmit={handleSubmit} className="ai-config__form">
              <div className="form__row">
                <div className="form__group form__group--half">
                  <label className="form__label" htmlFor="ai-providerName">
                    厂商名称
                  </label>
                  <input
                    id="ai-providerName"
                    className="form__input"
                    value={form.providerName}
                    onChange={(event) => updateForm('providerName', event.target.value)}
                    placeholder="例如 OpenAI"
                    disabled={saving}
                  />
                </div>
                <div className="form__group form__group--half">
                  <label className="form__label" htmlFor="ai-modelName">
                    模型名称
                  </label>
                  <input
                    id="ai-modelName"
                    className="form__input"
                    value={form.modelName}
                    onChange={(event) => updateForm('modelName', event.target.value)}
                    placeholder="例如 gpt-4o-mini"
                    disabled={saving}
                  />
                </div>
              </div>

              <div className="form__group">
                <label className="form__label" htmlFor="ai-apiUrl">
                  接口地址
                </label>
                <input
                  id="ai-apiUrl"
                  className="form__input"
                  value={form.apiUrl}
                  onChange={(event) => updateForm('apiUrl', event.target.value)}
                  placeholder="https://api.openai.com/v1/chat/completions"
                  disabled={saving}
                />
              </div>

              <div className="form__group">
                <label className="form__label" htmlFor="ai-apiKey">
                  API Key
                </label>
                <input
                  id="ai-apiKey"
                  type="password"
                  className="form__input"
                  value={form.apiKey}
                  onChange={(event) => updateForm('apiKey', event.target.value)}
                  placeholder={selectedConfig ? '留空表示沿用当前密钥' : '请输入 API Key'}
                  disabled={saving}
                />
                {selectedConfig?.apiKeyMasked && (
                  <p className="form__hint">当前已保存密钥：{selectedConfig.apiKeyMasked}</p>
                )}
              </div>

              <div className="form__row">
                <div className="form__group form__group--half">
                  <label className="form__label" htmlFor="ai-status">
                    状态
                  </label>
                  <select
                    id="ai-status"
                    className="form__select"
                    value={form.status}
                    onChange={(event) => updateForm('status', event.target.value as AiConfigStatus)}
                    disabled={saving}
                  >
                    <option value="DISABLED">DISABLED</option>
                    <option value="ENABLED">ENABLED</option>
                  </select>
                </div>
                <div className="form__group form__group--half">
                  <label className="form__label" htmlFor="ai-remark">
                    备注
                  </label>
                  <input
                    id="ai-remark"
                    className="form__input"
                    value={form.remark}
                    onChange={(event) => updateForm('remark', event.target.value)}
                    placeholder="例如 阅读短文生成"
                    disabled={saving}
                  />
                </div>
              </div>

              <label className="ai-config__checkbox">
                <input
                  type="checkbox"
                  checked={form.isDefault}
                  onChange={(event) => updateForm('isDefault', event.target.checked)}
                  disabled={saving}
                />
                <span>设为默认配置</span>
              </label>

              <div className="form__actions">
                <button className="btn btn--secondary" type="button" onClick={handleCreateMode} disabled={saving}>
                  清空
                </button>
                <button className="btn btn--primary" type="submit" disabled={saving}>
                  {saving ? '保存中...' : isEditing ? '保存修改' : '创建配置'}
                </button>
              </div>
            </form>

            <div className="ai-config__chat">
              <div className="ai-config__section-header">
                <div>
                  <p className="panel__eyebrow">Chat Test</p>
                  <h3 className="ai-config__section-title">对话测试</h3>
                </div>
              </div>

              <p className="form__hint">
                发送一条消息到当前选中的配置，用于验证模型、地址和 API Key 是否可用。
              </p>

              <textarea
                className="form__input ai-config__chat-input"
                value={chatInput}
                onChange={(event) => setChatInput(event.target.value)}
                placeholder="输入一条测试消息"
                disabled={chatSending}
              />

              <div className="form__actions">
                <button
                  className="btn btn--primary"
                  type="button"
                  onClick={() => void handleSendChat()}
                  disabled={chatSending || !selectedConfig}
                >
                  {chatSending ? '发送中...' : '发送测试消息'}
                </button>
              </div>

              <div className="ai-config__chat-log">
                {chatHistory.length === 0 ? (
                  <div className="ai-config__empty">还没有测试记录。保存并选中一条启用配置后，可以直接发起测试。</div>
                ) : (
                  chatHistory.map((exchange, index) => (
                    <div key={`${exchange.userMessage}-${index}`} className="ai-config__chat-item">
                      <div>
                        <p className="panel__eyebrow">User</p>
                        <p className="ai-config__chat-text">{exchange.userMessage}</p>
                      </div>
                      <div>
                        <p className="panel__eyebrow">Assistant</p>
                        <p className="ai-config__chat-text">{exchange.reply}</p>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
