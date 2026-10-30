import EmptyState from './EmptyState';

export default function DataTable({ columns, rows, keyField = 'id', emptyMessage = 'No records found.' }) {
  if (!rows?.length) return <EmptyState message={emptyMessage} />;
  return <div className="table-wrap"><table>
    <thead><tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr></thead>
    <tbody>{rows.map((row) => <tr key={row[keyField]}>{columns.map((column) =>
      <td key={column.key}>{column.render ? column.render(row) : row[column.key]}</td>)}</tr>)}</tbody>
  </table></div>;
}
