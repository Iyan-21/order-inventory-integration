const STATUS_CLASS = {
  CONFIRMED: 'status-confirmed',
  REJECTED: 'status-rejected',
  CANCELLED: 'status-cancelled',
};

export default function OrderHistory({ orders, onCancel, cancellingId }) {
  const safeOrders = Array.isArray(orders) ? orders : [];
  const sorted = [...safeOrders].sort((a, b) => b.orderId - a.orderId);

  return (
    <div className="panel">
      <h2 className="panel-title">Order history</h2>

      {sorted.length === 0 ? (
        <p className="cart-empty">No orders yet.</p>
      ) : (
        <ul className="order-list">
          {sorted.map((order) => (
            <li key={order.orderId} className="order-item">
              <div className="order-item-main">
                <span className="mono order-id">#{order.orderId}</span>
                <span className={`status-pill ${STATUS_CLASS[order.status] || ''}`}>
                  {order.status}
                </span>
              </div>

              {order.items?.length > 0 && (
                <div className="order-item-lines">
                  {order.items.map((line) => (
                    <span key={line.productId} className="order-line mono">
                      {line.productId} ×{line.quantity}
                    </span>
                  ))}
                </div>
              )}

              {order.reason && <p className="order-reason">{order.reason}</p>}

              {order.status === 'CONFIRMED' && (
                <button
                  type="button"
                  className="cancel-btn"
                  onClick={() => onCancel(order.orderId)}
                  disabled={cancellingId === order.orderId}
                >
                  {cancellingId === order.orderId ? 'Cancelling…' : 'Cancel'}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}