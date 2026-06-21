import React, { useEffect, useState } from 'react';
import { getTrending } from '../services/api';

// Format count scores into clean readables: e.g. 500000 -> 500K
function formatScore(score) {
  if (score >= 1000000) {
    return (score / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
  }
  if (score >= 1000) {
    return (score / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
  }
  return score.toString();
}

export default function TrendingPanel({ onSelectChip }) {
  const [trends, setTrends] = useState([]);
  const [error, setError] = useState(null);

  const fetchTrendingData = async () => {
    try {
      const list = await getTrending();
      setTrends(list);
      setError(null);
    } catch (err) {
      console.error("Failed to load trending data:", err);
      setError("Trending searches unavailable");
    }
  };

  useEffect(() => {
    fetchTrendingData();

    // Poll trending searches every 10 seconds to show dynamic boosts
    const interval = setInterval(fetchTrendingData, 10000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="trending-panel">
      <h3 className="trending-title">
        Trending Searches
      </h3>
      {error ? (
        <div className="trending-error">{error}</div>
      ) : (
        <div className="trending-chips-container">
          {trends.length === 0 ? (
            <p className="trending-placeholder">No trending searches found yet.</p>
          ) : (
            trends.map((item, index) => (
              <button
                key={`${item.query}-${index}`}
                onClick={() => onSelectChip(item.query)}
                className="trending-chip"
                title={`Rank #${index + 1} - Score: ${item.score}`}
              >
                <span className="chip-rank">#{index + 1}</span>
                <span className="chip-text">{item.query}</span>
                <span className="chip-score">{formatScore(item.score)} searches</span>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  );
}
