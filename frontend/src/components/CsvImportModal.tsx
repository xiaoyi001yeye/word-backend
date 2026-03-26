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

type ImportMode = 'csv' | 'json';

export function CsvImportModal({ isOpen, onClose, dictionary, onSuccess }: CsvImportModalProps) {
  const [mode, setMode] = useState<ImportMode>('csv');
  const [file, setFile] = useState<File | null>(null);
  const [jsonData, setJsonData] = useState('');
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

  const handleJsonChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setJsonData(e.target.value);
    setError(null);
    setResult(null);
  };

  const validateJson = (jsonString: string): boolean => {
    try {
      const parsed = JSON.parse(jsonString);
      if (!Array.isArray(parsed)) {
        setError('JSON数据必须是一个数组');
        return false;
      }
      if (parsed.length === 0) {
        setError('JSON数组不能为空');
        return false;
      }
      // 验证基本结构
      for (let i = 0; i < Math.min(parsed.length, 5); i++) {
        const item = parsed[i];
        if (!item.word || typeof item.word !== 'string') {
          setError(`第${i + 1}项缺少有效的单词字段`);
          return false;
        }
        if (!item.definition || typeof item.definition !== 'string') {
          setError(`第${i + 1}项缺少有效的定义字段`);
          return false;
        }
      }
      setError(null);
      return true;
    } catch (err) {
      setError('JSON格式无效: ' + (err instanceof Error ? err.message : '未知错误'));
      return false;
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (mode === 'csv' && !file) {
      setError('请选择要上传的CSV文件');
      return;
    }
    
    if (mode === 'json' && !jsonData.trim()) {
      setError('请输入JSON数据');
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      let response: WordListProcessResult;
      
      if (mode === 'csv') {
        const formData = new FormData();
        formData.append('file', file!);
        formData.append('hasHeader', hasHeader.toString());
        response = await dictionaryWordApi.importCsv(dictionary.id, formData);
      } else {
        // 验证JSON
        if (!validateJson(jsonData)) {
          return;
        }
        response = await dictionaryWordApi.importJson(dictionary.id, jsonData);
      }
      
      setResult(response);

      if (onSuccess) {
        onSuccess();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '导入失败');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setMode('csv');
    setFile(null);
    setJsonData('');
    setError(null);
    setResult(null);
    onClose();
  };

  const handleModeChange = (newMode: ImportMode) => {
    setMode(newMode);
    setFile(null);
    setJsonData('');
    setError(null);
    setResult(null);
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <h2 className="modal__title">导入单词到辞书</h2>
          <button className="modal__close" onClick={handleClose}>&times;</button>
        </div>
        
        <form onSubmit={handleSubmit} className="modal__form">
          {/* 模式选择 */}
          <div className="form__group">
            <div className="mode-toggle">
              <button
                type="button"
                className={`mode-button ${mode === 'csv' ? 'active' : ''}`}
                onClick={() => handleModeChange('csv')}
                disabled={loading}
              >
                CSV文件导入
              </button>
              <button
                type="button"
                className={`mode-button ${mode === 'json' ? 'active' : ''}`}
                onClick={() => handleModeChange('json')}
                disabled={loading}
              >
                JSON数据导入
              </button>
            </div>
          </div>

          {/* CSV导入模式 */}
          {mode === 'csv' && (
            <>
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
            </>
          )}

          {/* JSON导入模式 */}
          {mode === 'json' && (
            <>
              <div className="form__group">
                <label className="form__label">输入JSON数据 *</label>
                <textarea
                  value={jsonData}
                  onChange={handleJsonChange}
                  className="json-textarea"
                  placeholder='{"word": "example", "definition": "例子", "phonetic": "/ɪɡˈzæmpəl/", "partOfSpeech": "n.", "exampleSentence": "This is an example.", "translation": "这是一个例子", "difficulty": 2}'
                  rows={10}
                  disabled={loading}
                />
                <div className="form__hint">
                  <h4>JSON格式要求：</h4>
                  <ul>
                    <li>必须是JSON数组格式</li>
                    <li>每个对象必须包含word和definition字段</li>
                    <li>可选字段：phonetic, partOfSpeech, exampleSentence, translation, difficulty</li>
                    <li>difficulty范围：1-5</li>
                  </ul>
                </div>
              </div>
            </>
          )}



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
              disabled={loading || (mode === 'csv' ? !file : !jsonData.trim())}
            >
              {loading ? '导入中...' : '开始导入'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}