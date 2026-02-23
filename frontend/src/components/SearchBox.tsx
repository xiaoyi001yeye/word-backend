import { useState, useEffect, useRef } from 'react';
import './SearchBox.css';

interface SearchBoxProps {
  onLoading: (loading: boolean) => void;
  onClear: () => void;
  onSearchQueryChange?: (query: string) => void;
  value?: string;
}

export function SearchBox({ onLoading, onClear, onSearchQueryChange, value = '' }: SearchBoxProps) {
  const [query, setQuery] = useState(value);
  const [isSearching, setIsSearching] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const prevQueryRef = useRef(query);

  useEffect(() => {
    setQuery(value);
  }, [value]);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    const trimmedQuery = query.trim();
    prevQueryRef.current = trimmedQuery;

    if (!trimmedQuery) {
      onClear();
      if (onSearchQueryChange) {
        onSearchQueryChange('');
      }
      return;
    }

    setIsSearching(true);
    onLoading(true);

    debounceRef.current = setTimeout(() => {
      if (onSearchQueryChange) {
        onSearchQueryChange(trimmedQuery);
      }
      setIsSearching(false);
      onLoading(false);
    }, 300);

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [query, onLoading, onClear, onSearchQueryChange]);

  return (
    <div className="search-box">
      <span className="search-box__icon">
        {isSearching ? (
          <span className="search-box__spinner"></span>
        ) : (
          '🔍'
        )}
      </span>
      <input
        type="text"
        className="search-box__input"
        placeholder="搜索单词..."
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      {query && (
        <button
          className="search-box__clear"
          onClick={() => {
            setQuery('');
            onClear();
          }}
        >
          ×
        </button>
      )}
    </div>
  );
}
