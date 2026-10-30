import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import ClinicalWorkspace from './ClinicalWorkspace';
import { medicalRecordsApi } from '../../api/medicalRecords';
import { appointmentsApi } from '../../api/appointments';
import { laboratoryApi } from '../../api/laboratory';
const auth=vi.hoisted(()=>({user:{role:'PATIENT'}}));
vi.mock('../../context/AuthContext',()=>({useAuth:()=>auth}));
vi.mock('../../api/medicalRecords',()=>({medicalRecordsApi:{mine:vi.fn(),doctorMine:vi.fn(),create:vi.fn(),update:vi.fn()}}));
vi.mock('../../api/appointments',()=>({appointmentsApi:{doctorMine:vi.fn(),list:vi.fn()}}));
vi.mock('../../api/laboratory',()=>({laboratoryApi:{myOrders:vi.fn(),orders:vi.fn(),myResults:vi.fn(),results:vi.fn(),createOrder:vi.fn(),createResult:vi.fn()}}));
vi.mock('../../api/prescriptions',()=>({prescriptionsApi:{patientMine:vi.fn().mockResolvedValue([]),doctorMine:vi.fn().mockResolvedValue([]),list:vi.fn().mockResolvedValue([])}}));
function mount(kind='records'){render(<MemoryRouter><ClinicalWorkspace kind={kind}/></MemoryRouter>);}
beforeEach(()=>{vi.clearAllMocks();auth.user={role:'PATIENT'};medicalRecordsApi.mine.mockResolvedValue([]);medicalRecordsApi.doctorMine.mockResolvedValue([]);appointmentsApi.doctorMine.mockResolvedValue([]);appointmentsApi.list.mockResolvedValue([]);laboratoryApi.myOrders.mockResolvedValue([]);laboratoryApi.orders.mockResolvedValue([]);laboratoryApi.myResults.mockResolvedValue([]);laboratoryApi.results.mockResolvedValue([]);});
describe('Clinical role and data integration',()=>{
 it('patient sees own backend records and no creation form',async()=>{medicalRecordsApi.mine.mockResolvedValue([{id:'record-1',diagnosis:'Synthetic browser diagnosis',visitDate:'2026-10-30'}]);mount();expect(await screen.findByText('Synthetic browser diagnosis')).toBeInTheDocument();expect(screen.queryByRole('heading',{name:'Create clinical record'})).not.toBeInTheDocument();expect(medicalRecordsApi.doctorMine).not.toHaveBeenCalled();});
 it('failed service data is shown as an error, not an empty history',async()=>{medicalRecordsApi.mine.mockRejectedValue({response:{status:503,data:{message:'Service is unavailable'}}});mount();expect(await screen.findByRole('alert')).toHaveTextContent('Service is unavailable');expect(screen.queryByText('No authorized records were returned.')).not.toBeInTheDocument();});
 it('clinician creation derives profile IDs from an eligible assigned appointment',async()=>{auth.user={role:'DOCTOR'};appointmentsApi.doctorMine.mockResolvedValue([{id:'appointment-1',patientId:'patient-profile',doctorId:'doctor-profile',status:'IN_PROGRESS',appointmentDate:'2026-10-30',appointmentTime:'10:00',queueNumber:1,reason:'Synthetic demo'}]);medicalRecordsApi.create.mockResolvedValue({id:'record-new'});mount();await screen.findByRole('heading',{name:'Create clinical record'});fireEvent.change(screen.getByLabelText('Authorized appointment'),{target:{value:'appointment-1'}});fireEvent.change(screen.getByLabelText('diagnosis'),{target:{value:'Synthetic diagnosis'}});fireEvent.click(screen.getByRole('button',{name:'Create record'}));await waitFor(()=>expect(medicalRecordsApi.create).toHaveBeenCalledWith(expect.objectContaining({appointmentId:'appointment-1',patientId:'patient-profile',doctorId:'doctor-profile',diagnosis:'Synthetic diagnosis'})));});
 it('cancelled appointments are not eligible for a clinical record',async()=>{auth.user={role:'DOCTOR'};appointmentsApi.doctorMine.mockResolvedValue([{id:'cancelled',status:'CANCELLED'}]);mount();expect(await screen.findByText(/No eligible appointments/)).toBeInTheDocument();expect(screen.queryByLabelText('Authorized appointment')).not.toBeInTheDocument();});
 it('lab staff can publish results but cannot create clinical orders',async()=>{auth.user={role:'LAB_STAFF'};mount('laboratory');expect(await screen.findByRole('heading',{name:'Publish laboratory result'})).toBeInTheDocument();expect(screen.queryByRole('heading',{name:'Order a laboratory test'})).not.toBeInTheDocument();expect(appointmentsApi.doctorMine).not.toHaveBeenCalled();});
});
