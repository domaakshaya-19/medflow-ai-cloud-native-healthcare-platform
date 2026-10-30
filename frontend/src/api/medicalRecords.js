import { api } from '../api';

export const medicalRecordsApi = {
  create: (record) => api.post('/api/medical-records', record).then(({ data }) => data),
  mine: () => api.get('/api/medical-records/me').then(({ data }) => data),
  patient: (patientId) => api.get(`/api/medical-records/patient/${patientId}`).then(({ data }) => data),
  doctorMine: () => api.get('/api/medical-records/doctor/me').then(({ data }) => data),
  byAppointment: (appointmentId) => api.get(`/api/medical-records/appointment/${appointmentId}`).then(({ data }) => data),
  get: (id) => api.get(`/api/medical-records/${id}`).then(({ data }) => data),
  update: (id, record) => api.put(`/api/medical-records/${id}`, record).then(({ data }) => data),
  remove: (id) => api.delete(`/api/medical-records/${id}`),
};
