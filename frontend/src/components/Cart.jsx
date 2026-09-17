import { useState } from 'react';

const LOW_STOCK_THRESHOLD = 5;

export default function Cart({ inventory, onSubmit, submitting }) {
  const [productId, setProductId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [lines, setLines] = useState([]);

  const availableProducts = inventory.filter((item) => item.stock > 0);

  const addLine = () => {
    if (!productId || quantity < 1) return;

    setLines((prev) => {
      const existing = prev.find((l) => l.productId === productId);
      if (existing) {
        return prev.map((l) =>
          l.productId === productId ? { ...l, quantity: l.quantity + Number(quantity) } : l
        );
      }
      return [...prev, { productId, quantity: Number(quantity) }];
    });

    setQuantity(1);
  };

  const removeLine = (id) => {
    setLines((prev) => prev.filter((l) => l.productId !== id));
  };

  const handleSubmit = async () => {
    if (lines.length === 0) return;
    await onSubmit(lines);
    setLines([]);
  };

  const nameFor = (id) => inventory.find((i) => i.productId === id)?.name || id;

  return (
    <div className="panel">
      <h2 className="panel-title">New order</h2>

      <div className="cart-add-row">
        <div className="select-wrap cart-select">
          <select value={productId} onChange={(e) => setProductId(e.target.value)}>
            <option value="" disabled>
              Select product…
            </option>
            {availableProducts.map((item) => (
              <option key={item.productId} value={item.productId}>
                {item.productId} — {item.name} ({item.stock}
                {item.stock < LOW_STOCK_THRESHOLD ? ' — low' : ''})
              </option>
            ))}
          </select>
        </div>

        <input
          type="number"
          min="1"
          className="cart-qty-input"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
        />

        <button type="button" className="add-btn" onClick={addLine} disabled={!productId}>
          Add
        </button>
      </div>

      {lines.length === 0 ? (
        <p className="cart-empty">Cart is empty. Add a product above.</p>
      ) : (
        <ul className="cart-list">
          {lines.map((line) => (
            <li key={line.productId} className="cart-item">
              <span className="cart-item-name">
                {line.productId} — {nameFor(line.productId)}
              </span>
              <span className="cart-item-qty">×{line.quantity}</span>
              <button
                type="button"
                className="cart-remove-btn"
                onClick={() => removeLine(line.productId)}
                aria-label={`Remove ${line.productId}`}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}

      <button
        type="button"
        className="submit-btn"
        disabled={lines.length === 0 || submitting}
        onClick={handleSubmit}
      >
        {submitting ? 'Placing order…' : 'Place order'}
      </button>
    </div>
  );
}