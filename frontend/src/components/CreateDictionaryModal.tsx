import { useState } from 'react';
import { dictionaryApi } from '../api';
import type { Dictionary } from '../types';

interface CreateDictionaryModalProps {
  isOpen: boolean;
  onClose: () => void;
  onDictionaryCreated: (dictionary: Dictionary) => void;
}

const CATEGORIES = [
  '高考', '考研', '四级', '六级', '雅思', '托福', 'GRE', '中考', '专四', '专八',
  '初中', '高中', '小学', 'BEC', 'PETS', 'SAT', 'GMAT', 'MBA', '考博', '其他'
];

export function CreateDictionaryModal({ isOpen, onClose, onDictionaryCreated }: CreateDictionaryModalProps) {
  const [formData, setFormData] = useState({
    name: '',
    filePath: '',
    fileSize: 0,
    category: '其他',
    wordCount: 0
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'fileSize' || name === 'wordCount' ? parseInt(value) || 0 : value
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (!formData.name.trim()) {
        throw new Error('辞书名称不能为空');
      }

      const newDictionary = await dictionaryApi.create(formData);
      onDictionaryCreated(newDictionary);
      handleClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : '创建辞书失败');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setFormData({
      name: '',
      filePath: '',
      fileSize: 0,
      category: '其他',
      wordCount: 0
    });
    setError(null);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal">
        <div className="modal__header">
          <h2 className="modal__title">创建新辞书</h2>
          <button className="modal__close" onClick={handleClose}>&times;</button>
        </div>
        
        <form onSubmit={handleSubmit} className="modal__form">
          <div className="form__group">
            <label htmlFor="name" className="form__label">
              辞书名称 *
            </label>
            <input
              type="text"
              id="name"
              name="name"
              value={formData.name}
              onChange={handleInputChange}
              className="form__input"
              placeholder="请输入辞书名称"
              required
              disabled={loading}
            />
          </div>

          <div className="form__group">
            <label htmlFor="category" className="form__label">
              分类
            </label>
            <select
              id="category"
              name="category"
              value={formData.category}
              onChange={handleInputChange}
              className="form__select"
              disabled={loading}
            >
              {CATEGORIES.map(category => (
                <option key={category} value={category}>
                  {category}
                </option>
              ))}
            </select>
          </div>

          <div className="form__group">
            <label htmlFor="filePath" className="form__label">
              文件路径
            </label>
            <input
              type="text"
              id="filePath"
              name="filePath"
              value={formData.filePath}
              onChange={handleInputChange}
              className="form__input"
              placeholder="可选：文件路径"
              disabled={loading}
            />
          </div>

          <div className="form__row">
            <div className="form__group form__group--half">
              <label htmlFor="fileSize" className="form__label">
                文件大小 (字节)
              </label>
              <input
                type="number"
                id="fileSize"
                name="fileSize"
                value={formData.fileSize}
                onChange={handleInputChange}
                className="form__input"
                min="0"
                disabled={loading}
              />
            </div>

            <div className="form__group form__group--half">
              <label htmlFor="wordCount" className="form__label">
                单词数量
              </label>
              <input
                type="number"
                id="wordCount"
                name="wordCount"
                value={formData.wordCount}
                onChange={handleInputChange}
                className="form__input"
                min="0"
                disabled={loading}
              />
            </div>
          </div>

          {error && (
            <div className="form__error">
              {error}
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
              disabled={loading}
            >
              {loading ? '创建中...' : '创建辞书'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}