import { api } from '../api';

export const authApi = {
  login: (credentials) => api.post('/api/auth/login', credentials, { skipAuth: true }).then(({ data }) => data),
  register: (registration) => api.post('/api/auth/register', registration, { skipAuth: true }).then(({ data }) => data),
  me: () => api.get('/api/auth/me').then(({ data }) => data),
  createStaff: (staff) => api.post('/api/auth/staff', staff).then(({ data }) => data),
};
