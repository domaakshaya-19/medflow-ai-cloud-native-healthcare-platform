import { api } from '../api';

export const doctorsApi = {
  create: (profile) => api.post('/api/doctors', profile).then(({ data }) => data),
  me: () => api.get('/api/doctors/me').then(({ data }) => data),
  get: (id) => api.get(`/api/doctors/${id}`).then(({ data }) => data),
  update: (id, profile) => api.put(`/api/doctors/${id}`, profile).then(({ data }) => data),
  remove: (id) => api.delete(`/api/doctors/${id}`),
  list: () => api.get('/api/doctors').then(({ data }) => data),
  directory: (specialization = '') => api.get('/api/doctors/directory', {
    params: specialization.trim() ? { specialization: specialization.trim() } : {},
  }).then(({ data }) => data),
  directoryEntry: (id) => api.get(`/api/doctors/directory/${id}`).then(({ data }) => data),
};
