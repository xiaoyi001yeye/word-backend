import { useState, useEffect, useRef } from 'react';
import { metaWordApi } from '../api';
import type { MetaWord } from '../types';
import './SearchBox.css';

interface SearchBoxProps {
  onSearchResults: (words: MetaWord[]) => void;
  onLoading: (loading: boolean) => void;
  onClear: () => void;
}

export function SearchBox({ onSearchResults, onLoading, onClear }: SearchBoxProps) {
  const [query, setQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }

    if (!query.trim()) {
      onClear();
      return;
    }

    setIsSearching(true);
    onLoading(true);

    debounceRef.current = setTimeout(async () => {
      try {
        const results = await metaWordApi.search(query.trim());
        onSearchResults(results);
      } catch (error) {
        console.error('Search failed:', error);
        onSearchResults([]);
      } finally {
        setIsSearching(false);
        onLoading(false);
      }
    }, 300);

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
  }, [query, onSearchResults, onLoading, onClear]);

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
