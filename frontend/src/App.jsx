import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import { useAuth } from './context/AuthContext';
import AppShell from './layouts/AppShell';
import Account from './pages/Account';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import ClinicalWorkspace from './pages/clinical/ClinicalWorkspace';
import StaffProvision from './pages/StaffProvision';
import DoctorProfile from './pages/doctors/DoctorProfile';
import AiAssistant from './pages/AiAssistant';
import BusinessWorkspace from './pages/BusinessWorkspace';
import PatientProfileGuard from './components/PatientProfileGuard';
import PatientProfileSetup from './pages/patient/PatientProfileSetup';
import PatientRoutePage from './pages/patient/PatientRoutePage';
import DoctorDirectory from './pages/doctors/DoctorDirectory';
import DoctorDetail from './pages/doctors/DoctorDetail';
import AppointmentsPage from './pages/appointments/AppointmentsPage';
import AppointmentBooking from './pages/appointments/AppointmentBooking';
import AppointmentDetail from './pages/appointments/AppointmentDetail';
import Register from './pages/Register';

const protectedPage = (element, roles) => <ProtectedRoute roles={roles}>{element}</ProtectedRoute>;
export default function App() {
  const { user } = useAuth();
  return <Routes><Route path="/login" element={user ? <Navigate to="/dashboard" replace /> : <Login />} />
    <Route path="/register" element={user ? <Navigate to="/dashboard" replace /> : <Register />} />
    <Route element={protectedPage(<AppShell />)}>
      <Route path="/patient/setup" element={protectedPage(<PatientProfileSetup />, ['PATIENT'])} />
      <Route path="/doctor/profile" element={protectedPage(<DoctorProfile />, ['DOCTOR'])} />
      <Route path="/admin/staff" element={protectedPage(<StaffProvision />, ['ADMIN'])} />
      <Route path="/assistant" element={<PatientProfileGuard><AiAssistant /></PatientProfileGuard>} />
      {['pharmacy','nutrition','billing','emergency','notifications'].map(kind=><Route key={kind} path={`/${kind}`} element={protectedPage(<PatientProfileGuard><BusinessWorkspace kind={kind}/></PatientProfileGuard>,kind==='billing'?['PATIENT','BILLING_STAFF','ADMIN']:kind==='emergency'?['PATIENT','EMERGENCY_STAFF','ADMIN']:kind==='pharmacy'?['PATIENT','DOCTOR','PHARMACIST','ADMIN']:kind==='nutrition'?['PATIENT','DOCTOR','ADMIN']:undefined)} />)}
      <Route path="/dashboard" element={<PatientProfileGuard><Dashboard /></PatientProfileGuard>} /><Route path="/account" element={<PatientProfileGuard><Account /></PatientProfileGuard>} />
      <Route path="/patients" element={protectedPage(<PatientProfileGuard><PatientRoutePage /></PatientProfileGuard>, ['PATIENT','ADMIN'])} />
      <Route path="/doctors" element={protectedPage(<PatientProfileGuard><DoctorDirectory /></PatientProfileGuard>, ['PATIENT','DOCTOR','ADMIN'])} />
      <Route path="/doctors/:id" element={protectedPage(<PatientProfileGuard><DoctorDetail /></PatientProfileGuard>, ['PATIENT','DOCTOR','ADMIN'])} />
      <Route path="/appointments" element={protectedPage(<PatientProfileGuard><AppointmentsPage /></PatientProfileGuard>, ['PATIENT','DOCTOR','ADMIN'])} />
      <Route path="/appointments/new" element={protectedPage(<PatientProfileGuard><AppointmentBooking /></PatientProfileGuard>, ['PATIENT'])} />
      <Route path="/appointments/:id" element={protectedPage(<PatientProfileGuard><AppointmentDetail /></PatientProfileGuard>, ['PATIENT','DOCTOR','ADMIN'])} />
      <Route path="/medical-records" element={protectedPage(<PatientProfileGuard><ClinicalWorkspace kind="records" /></PatientProfileGuard>, ['PATIENT','DOCTOR','ADMIN'])} />
      <Route path="/laboratory" element={protectedPage(<PatientProfileGuard><ClinicalWorkspace kind="laboratory" /></PatientProfileGuard>, ['PATIENT','DOCTOR','LAB_STAFF','ADMIN'])} /><Route path="/prescriptions" element={protectedPage(<PatientProfileGuard><ClinicalWorkspace kind="prescriptions" /></PatientProfileGuard>, ['PATIENT','DOCTOR','ADMIN'])} />
    </Route><Route path="*" element={<Navigate to={user ? '/dashboard' : '/login'} replace />} /></Routes>;
}
