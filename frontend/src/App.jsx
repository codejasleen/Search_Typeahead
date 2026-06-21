import React, { useState } from 'react';
import SearchBar from './components/SearchBar';
import TrendingPanel from './components/TrendingPanel';
import { postSearch } from './services/api';
import './App.css';

export default function App() {
  const [query, setQuery] = useState('');
  const [lastSearch, setLastSearch] = useState('');
  const [notification, setNotification] = useState('');
  const [trendingResetKey, setTrendingResetKey] = useState(0); // force refresh trending

  const handleSearchSubmit = async (searchQuery) => {
    try {
      setLastSearch(searchQuery);
      
      // Submit POST search request to the backend BatchService queue
      const res = await postSearch(searchQuery);
      
      showNotification(`Search recorded for "${searchQuery}"! Will be aggregated in next flush batch.`);
      setQuery(''); // clear search input
      
      // Trigger trending panel update
      setTimeout(() => {
        setTrendingResetKey(prev => prev + 1);
      }, 500);
    } catch (err) {
      console.error(err);
      showNotification(`Failed to record search: ${err.message}`, true);
    }
  };

  const showNotification = (msg, isError = false) => {
    setNotification(msg);
    setTimeout(() => {
      setNotification('');
    }, 4000);
  };

  const handleSelectQuery = (selectedQuery) => {
    setQuery(selectedQuery);
    handleSearchSubmit(selectedQuery);
  };

  return (
    <div className="app-container">
      <header className="app-header">
        <h1 className="app-title-gradient">Search Autocomplete</h1>
      </header>

      <main className="app-main-content">
        {notification && (
          <div className="notification-banner">
            <span className="notif-icon">⚡</span>
            <span className="notif-text">{notification}</span>
          </div>
        )}

        <div className="search-card-glass">
          <SearchBar
            query={query}
            setQuery={setQuery}
            onSearch={handleSearchSubmit}
          />
          {lastSearch && (
            <div className="last-search-log">
              Last submitted search: <strong className="highlight">{lastSearch}</strong>
            </div>
          )}
        </div>

        <div className="trending-card-glass">
          <TrendingPanel key={trendingResetKey} onSelectChip={handleSelectQuery} />
        </div>
      </main>

      <footer className="app-footer">
        <div className="tech-badge-container">
          <span className="tech-badge spring">Spring Boot 3.2.5</span>
          <span className="tech-badge react">React + Vite</span>
          <span className="tech-badge redis">Redis (3-Node Shards)</span>
          <span className="tech-badge h2">H2 (SQL Primary)</span>
        </div>
      </footer>
    </div>
  );
}
