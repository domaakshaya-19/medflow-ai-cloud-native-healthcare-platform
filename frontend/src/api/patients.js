import { api } from '../api';

export const patientsApi = {
  create: (profile) => api.post('/api/patients', profile).then(({ data }) => data),
  me: () => api.get('/api/patients/me').then(({ data }) => data),
  byAuthUser: (authUserId) => api.get(`/api/patients/by-auth-user/${authUserId}`).then(({ data }) => data),
  get: (id) => api.get(`/api/patients/${id}`).then(({ data }) => data),
  update: (id, profile) => api.put(`/api/patients/${id}`, profile).then(({ data }) => data),
  remove: (id) => api.delete(`/api/patients/${id}`),
  list: () => api.get('/api/patients').then(({ data }) => data),
};
