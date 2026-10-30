import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const navigation = [
  { to:'/pharmacy',label:'Pharmacy',icon:'✚',roles:['PATIENT','DOCTOR','PHARMACIST','ADMIN'] },
  { to:'/nutrition',label:'Nutrition & wellness',icon:'♧',roles:['PATIENT','DOCTOR','ADMIN'] },
  { to:'/billing',label:'Billing',icon:'▤',roles:['PATIENT','BILLING_STAFF','ADMIN'] },
  { to:'/emergency',label:'Emergency requests',icon:'!',roles:['PATIENT','EMERGENCY_STAFF','ADMIN'] },
  { to:'/notifications',label:'Notifications',icon:'◉',roles:['PATIENT','DOCTOR','NURSE','RECEPTIONIST','LAB_STAFF','PHARMACIST','BILLING_STAFF','EMERGENCY_STAFF','ADMIN'] },
  { to: '/assistant', label: 'AI assistant', icon: '◇', roles: ['PATIENT','DOCTOR','NURSE','RECEPTIONIST','LAB_STAFF','PHARMACIST','BILLING_STAFF','EMERGENCY_STAFF','ADMIN'] },
  { to: '/doctor/profile', label: 'My professional profile', icon: '✚', roles: ['DOCTOR'] },
  { to: '/admin/staff', label: 'Staff accounts', icon: '♙', roles: ['ADMIN'] },
  { to: '/dashboard', label: 'Overview', icon: '⌂', roles: ['PATIENT', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'LAB_STAFF', 'PHARMACIST', 'BILLING_STAFF', 'EMERGENCY_STAFF', 'ADMIN'] },
  { to: '/patients', label: 'Patients', icon: '♙', roles: ['PATIENT', 'ADMIN'] },
  { to: '/doctors', label: 'Doctors', icon: '✚', roles: ['PATIENT', 'DOCTOR', 'ADMIN'] },
  { to: '/appointments', label: 'Appointments', icon: '□', roles: ['PATIENT', 'DOCTOR', 'ADMIN'] },
  { to: '/medical-records', label: 'Medical records', icon: '▤', roles: ['PATIENT', 'DOCTOR', 'ADMIN'] },
  { to: '/laboratory', label: 'Laboratory', icon: '◈', roles: ['PATIENT', 'DOCTOR', 'LAB_STAFF', 'ADMIN'] },
  { to: '/prescriptions', label: 'Prescriptions', icon: '✎', roles: ['PATIENT', 'DOCTOR', 'ADMIN'] },
];

export default function AppShell() {
  const { user, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const menuRef = useRef(null);
  const items = navigation.filter((item) => item.roles.includes(user.role));
  useEffect(() => {
    const close = (event) => { if (!menuRef.current?.contains(event.target)) setProfileOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  return <div className="app-shell">
    <aside className={mobileOpen ? 'app-sidebar sidebar-open' : 'app-sidebar'}>
      <div className="brand"><span className="brand-mark">M+</span><span>MedFlow AI</span></div>
      <nav aria-label="Main navigation">{items.map((item) => <NavLink key={item.to} to={item.to} onClick={() => setMobileOpen(false)}
        className={({ isActive }) => isActive ? 'nav-link nav-active' : 'nav-link'}><span aria-hidden="true">{item.icon}</span>{item.to === '/patients' && user.role === 'PATIENT' ? 'My profile' : item.label}</NavLink>)}</nav>
      <div className="sidebar-foot"><span className="secure-dot" /> Secure healthcare workspace</div>
    </aside>
    {mobileOpen && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
    <div className="app-content">
      <header className="app-header">
        <button className="icon-button mobile-menu" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>☰</button>
        <div><p className="eyebrow">Healthcare workspace</p><strong>{user.role.replaceAll('_', ' ')}</strong></div>
        <div className="profile-menu" ref={menuRef}>
          <button className="profile-trigger" aria-expanded={profileOpen} onClick={() => setProfileOpen((open) => !open)}>
            <span className="mini-avatar">{initials(user.fullName)}</span><span className="profile-copy"><strong>{user.fullName}</strong><small>{user.email}</small></span><span>⌄</span>
          </button>
          {profileOpen && <div className="profile-popover"><NavLink to="/account" onClick={() => setProfileOpen(false)}>Account details</NavLink><button onClick={logout}>Log out</button></div>}
        </div>
      </header>
      <main className="page-content"><Outlet /></main>
    </div>
  </div>;
}

function initials(name = '') { return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase() || 'MF'; }
