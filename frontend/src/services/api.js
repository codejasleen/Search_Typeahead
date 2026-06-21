/**
 * API Service for Search Typeahead
 */

export async function getSuggestions(prefix) {
  const res = await fetch(`/api/suggest?q=${encodeURIComponent(prefix)}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch suggestions: ${res.statusText}`);
  }
  return res.json();
}

export async function postSearch(query) {
  const res = await fetch('/api/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query }),
  });
  if (!res.ok) {
    throw new Error(`Failed to submit search: ${res.statusText}`);
  }
  return res.json();
}

export async function getTrending() {
  const res = await fetch('/api/trending?limit=10');
  if (!res.ok) {
    throw new Error(`Failed to fetch trending searches: ${res.statusText}`);
  }
  return res.json();
}

export async function getCacheDebug(prefix) {
  const res = await fetch(`/api/cache/debug?prefix=${encodeURIComponent(prefix)}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch cache debug: ${res.statusText}`);
  }
  return res.json();
}
