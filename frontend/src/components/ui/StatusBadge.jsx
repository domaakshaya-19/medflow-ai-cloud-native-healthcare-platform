export default function StatusBadge({ status }) {
  const value = String(status || 'unknown');
  return <span className={`status-badge status-${value.toLowerCase().replaceAll('_', '-')}`}>{value.replaceAll('_', ' ')}</span>;
}
