import ModulePending from '../modules/ModulePending';
import { useAuth } from '../../context/AuthContext';
import PatientProfilePage from './PatientProfilePage';

export default function PatientRoutePage(){const{user}=useAuth();return user.role==='PATIENT'?<PatientProfilePage/>:<ModulePending title="Patient management"/>;}
