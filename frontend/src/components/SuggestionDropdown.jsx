import React from 'react';

// Format count scores into clean readables: e.g. 500000 -> 500K, 1200000 -> 1.2M
function formatScore(score) {
  if (score >= 1000000) {
    return (score / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
  }
  if (score >= 1000) {
    return (score / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
  }
  return score.toString();
}

export default function SuggestionDropdown({ suggestions, onSelect }) {
  if (!suggestions || suggestions.length === 0) {
    return null;
  }

  return (
    <div className="suggestion-dropdown-card">
      <ul className="suggestion-list">
        {suggestions.map((item, index) => (
          <li
            key={`${item.query}-${index}`}
            onClick={() => onSelect(item.query)}
            className="suggestion-item"
          >
            <span className="suggestion-text">
              <span className="suggestion-bullet">↳</span> {item.query}
            </span>
            <span className="suggestion-score" title={`Search frequency: ${item.score}`}>
              {formatScore(item.score)} searches
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
