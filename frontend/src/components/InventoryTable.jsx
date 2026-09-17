const LOW_STOCK_THRESHOLD = 5;

export default function InventoryTable({ inventory }) {
  return (
    <div className="panel">
      <h2 className="panel-title">Inventory</h2>

      <table className="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Stock</th>
          </tr>
        </thead>
        <tbody>
          {inventory.map((item) => {
            const low = item.stock < LOW_STOCK_THRESHOLD;
            return (
              <tr key={item.productId} className={low ? 'row-low-stock' : ''}>
                <td className="mono">{item.productId}</td>
                <td>{item.name}</td>
                <td className="mono">
                  {item.stock}
                  {low && <span className="low-badge">LOW</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}