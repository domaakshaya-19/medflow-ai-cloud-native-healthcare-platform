import axios from 'axios';

export const TOKEN_KEY = 'medflow_token';
export const SESSION_EXPIRED_EVENT = 'medflow:session-expired';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const token = config.skipAuth ? null : localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.skipAuth && localStorage.getItem(TOKEN_KEY)) {
      localStorage.removeItem(TOKEN_KEY);
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  },
);

export function apiError(error, fallback = 'Something went wrong. Please try again.') {
  const data = error.response?.data;
  const fieldMessages = data?.fields || data?.fieldErrors;
  return {
    status: error.response?.status,
    message: data?.message || (error.code === 'ECONNABORTED' ? 'The service took too long to respond.' : fallback),
    fields: fieldMessages && typeof fieldMessages === 'object' ? fieldMessages : {},
  };
}
