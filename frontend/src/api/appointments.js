import { api } from '../api';

export const appointmentsApi = {
  create: (appointment) => api.post('/api/appointments', appointment).then(({ data }) => data),
  mine: () => api.get('/api/appointments/me').then(({ data }) => data),
  doctorMine: () => api.get('/api/appointments/doctor/me').then(({ data }) => data),
  doctorQueue: () => api.get('/api/appointments/doctor/me/queue').then(({ data }) => data),
  get: (id) => api.get(`/api/appointments/${id}`).then(({ data }) => data),
  update: (id, appointment) => api.put(`/api/appointments/${id}`, appointment).then(({ data }) => data),
  cancel: (id) => api.delete(`/api/appointments/${id}`),
  list: () => api.get('/api/appointments').then(({ data }) => data),
};
