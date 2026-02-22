import type { Dictionary } from '../types';
import './DictionaryCard.css';

interface DictionaryCardProps {
  dictionary: Dictionary;
  isSelected: boolean;
  onClick: () => void;
}

export function DictionaryCard({ dictionary, isSelected, onClick }: DictionaryCardProps) {
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

  return (
    <div className={`dictionary-card ${isSelected ? 'selected' : ''}`} onClick={onClick}>
      <div className="dictionary-card__header">
        <h3 className="dictionary-card__title">{dictionary.name}</h3>
        {dictionary.category && (
          <span className="dictionary-card__category">{dictionary.category}</span>
        )}
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
