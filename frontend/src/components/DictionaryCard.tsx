import type { Dictionary } from '../types';
import './DictionaryCard.css';

interface DictionaryCardProps {
  dictionary: Dictionary;
  isSelected: boolean;
  onClick: () => void;
  onDelete?: () => void;
  onAddJson?: () => void;
  onImportCsv?: () => void;
}

export function DictionaryCard({ dictionary, isSelected, onClick, onDelete, onAddJson, onImportCsv }: DictionaryCardProps) {
  const formatFileSize = (bytes?: number) => {
    if (!bytes) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    let size = bytes;
    let unitIndex = 0;
    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }
    return `${size.toFixed(1)} ${units[unitIndex]}`;
  };

  const handleDeleteClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onDelete) {
      onDelete();
    }
  };

  const handleAddJsonClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onAddJson) {
      onAddJson();
    }
  };

  const handleImportCsvClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (onImportCsv) {
      onImportCsv();
    }
  };

  return (
    <div className={`dictionary-card ${isSelected ? 'selected' : ''}`} onClick={onClick}>
      <div className="dictionary-card__header">
        <h3 className="dictionary-card__title">{dictionary.name}</h3>
        <div className="dictionary-card__header-right">
          <div className="dictionary-card__actions">
            {onAddJson && (
              <button
                className="dictionary-card__add-btn"
                onClick={handleAddJsonClick}
                title="添加JSON单词"
                aria-label="添加JSON单词"
              >
                📝
              </button>
            )}
            {onImportCsv && (
              <button
                className="dictionary-card__import-btn"
                onClick={handleImportCsvClick}
                title="导入CSV文件"
                aria-label="导入CSV文件"
              >
                📄
              </button>
            )}
            {dictionary.creationType === 'USER_CREATED' && onDelete && (
              <button
                className="dictionary-card__delete-btn"
                onClick={handleDeleteClick}
                title="删除辞书"
                aria-label="删除辞书"
              >
                ×
              </button>
            )}
          </div>
          {dictionary.category && (
            <span className="dictionary-card__category">{dictionary.category}</span>
          )}
        </div>
      </div>
      <div className="dictionary-card__stats">
        <div className="dictionary-card__stat">
          <span className="dictionary-card__stat-value">{dictionary.wordCount || 0}</span>
          <span className="dictionary-card__stat-label">单词</span>
        </div>
        <div className="dictionary-card__stat">
          <span className="dictionary-card__stat-value">{formatFileSize(dictionary.fileSize)}</span>
          <span className="dictionary-card__stat-label">大小</span>
        </div>
      </div>
    </div>
  );
}
