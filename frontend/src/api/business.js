import { api } from '../api';
export const business = {
  get: path => api.get(path).then(r => r.data),
  post: (path, body) => api.post(path, body).then(r => r.data),
  put: (path, body) => api.put(path, body).then(r => r.data),
};
