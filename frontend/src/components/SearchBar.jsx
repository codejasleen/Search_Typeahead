import React, { useEffect, useState, useRef } from 'react';
import { getSuggestions, getCacheDebug } from '../services/api';
import SuggestionDropdown from './SuggestionDropdown';

export default function SearchBar({ query, setQuery, onSearch }) {
  const [suggestions, setSuggestions] = useState([]);
  const [showDropdown, setShowDropdown] = useState(false);
  const [loading, setLoading] = useState(false);
  const [debugInfo, setDebugInfo] = useState(null);
  const dropdownRef = useRef(null);
  const debounceTimer = useRef(null);

  // Clear suggestions and debug info when query is cleared externally
  useEffect(() => {
    if (!query) {
      setSuggestions([]);
      setDebugInfo(null);
    }
  }, [query]);

  const handleInputChange = (e) => {
    const value = e.target.value;
    setQuery(value);
    setShowDropdown(true);

    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
    }

    if (!value.trim()) {
      setSuggestions([]);
      setDebugInfo(null);
      setLoading(false);
      return;
    }

    setLoading(true);
    debounceTimer.current = setTimeout(async () => {
      try {
        const start = performance.now();
        const list = await getSuggestions(value);
        const end = performance.now();
        const latencyMs = (end - start).toFixed(1);
        setSuggestions(list);

        // Fetch cache debug information for the current prefix input
        const debug = await getCacheDebug(value);
        setDebugInfo({ ...debug, latencyMs });
      } catch (err) {
        console.error("Error loading suggestions:", err);
      } finally {
        setLoading(false);
      }
    }, 300);
  };

  const handleSubmit = (e) => {
    if (e) e.preventDefault();
    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
    }
    setShowDropdown(false);
    if (query.trim()) {
      onSearch(query.trim());
    }
  };

  const handleSelectSuggestion = (suggestionQuery) => {
    setQuery(suggestionQuery);
    setShowDropdown(false);
    onSearch(suggestionQuery);
  };

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div className="search-bar-container" ref={dropdownRef}>
      <form onSubmit={handleSubmit} className="search-form">
        <div className="input-wrapper">
          <input
            type="text"
            value={query}
            onChange={handleInputChange}
            onFocus={() => {
              if (query.trim()) {
                setShowDropdown(true);
              }
            }}
            placeholder="Type your search here..."
            className="search-input"
          />
          {loading && <span className="input-spinner"></span>}
          <button type="submit" className="search-submit-btn">Search</button>
        </div>
      </form>

      {showDropdown && suggestions.length > 0 && (
        <SuggestionDropdown
          suggestions={suggestions}
          onSelect={handleSelectSuggestion}
        />
      )}

      {/* Embedded Prefix Cache Debug Indicator */}
      {query.trim() && debugInfo && (
        <div className={`cache-debug-indicator ${debugInfo.hit ? 'hit' : 'miss'}`}>
          <span className="indicator-pulse"></span>
          Prefix <strong>"{debugInfo.prefix}"</strong> sharded to 
          <span className="debug-node-name"> {debugInfo.cacheNode}</span> 
          <span className="debug-status-label">{debugInfo.hit ? ' (CACHE HIT)' : ' (CACHE MISS)'}</span>
          {debugInfo.latencyMs && (
            <span className="debug-latency"> in <strong>{debugInfo.latencyMs}ms</strong></span>
          )}
        </div>
      )}
    </div>
  );
}
