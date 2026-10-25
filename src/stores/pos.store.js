import { create } from 'zustand';

const createEmptyBill = (index) => ({
  index,
  items: [],
  customer: null,
  discountType: 'FIXED', // 'FIXED' or 'PERCENT'
  discountValue: 0,      // in Rupees or %
  paymentMethod: 'CASH',
  isFullPayment: true,
  amountTenderedPaise: 0,
});

export const usePosStore = create((set, get) => ({
  activeBillIndex: 0,
  bills: Array.from({ length: 9 }, (_, i) => createEmptyBill(i)),

  switchBill: (index) => {
    if (index >= 0 && index < 9) {
      set({ activeBillIndex: index });
    }
  },

  getCurrentBill: () => {
    const { bills, activeBillIndex } = get();
    return bills[activeBillIndex] || createEmptyBill(activeBillIndex);
  },

  addItem: (product) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const currentBill = bills[activeBillIndex];
      const existingIndex = currentBill.items.findIndex((it) => it.productId === product.id);

      let newItems;
      if (existingIndex >= 0) {
        newItems = currentBill.items.map((it, idx) =>
          idx === existingIndex ? { ...it, quantity: it.quantity + 1 } : it
        );
      } else {
        newItems = [
          ...currentBill.items,
          {
            productId: product.id,
            name: product.name,
            sku: product.sku || '',
            unitPricePaise: product.mrp_paise,
            mrpPaise: product.mrp_paise,
            costPricePaise: product.cost_price_paise || 0,
            taxRate: product.tax_rate || 0,
            quantity: 1,
            discountPaise: 0,
          },
        ];
      }

      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = {
        ...currentBill,
        items: newItems,
      };

      return { bills: updatedBills };
    });
  },

  updateItemQty: (productId, quantity) => {
    if (quantity <= 0) {
      get().removeItem(productId);
      return;
    }
    set((state) => {
      const { bills, activeBillIndex } = state;
      const currentBill = bills[activeBillIndex];
      const newItems = currentBill.items.map((it) =>
        it.productId === productId ? { ...it, quantity } : it
      );
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = { ...currentBill, items: newItems };
      return { bills: updatedBills };
    });
  },

  updateItemPrice: (productId, unitPricePaise) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const currentBill = bills[activeBillIndex];
      const newItems = currentBill.items.map((it) =>
        it.productId === productId ? { ...it, unitPricePaise: Math.max(0, unitPricePaise) } : it
      );
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = { ...currentBill, items: newItems };
      return { bills: updatedBills };
    });
  },

  updateItemDiscount: (productId, discountPaise) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const currentBill = bills[activeBillIndex];
      const newItems = currentBill.items.map((it) =>
        it.productId === productId ? { ...it, discountPaise: Math.max(0, discountPaise) } : it
      );
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = { ...currentBill, items: newItems };
      return { bills: updatedBills };
    });
  },

  removeItem: (productId) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const currentBill = bills[activeBillIndex];
      const newItems = currentBill.items.filter((it) => it.productId !== productId);
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = { ...currentBill, items: newItems };
      return { bills: updatedBills };
    });
  },

  setCustomer: (customer) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = { ...bills[activeBillIndex], customer };
      return { bills: updatedBills };
    });
  },

  setDiscount: (discountType, discountValue) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = {
        ...bills[activeBillIndex],
        discountType,
        discountValue: Math.max(0, Number(discountValue) || 0),
      };
      return { bills: updatedBills };
    });
  },

  setPaymentMethod: (paymentMethod) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = { ...bills[activeBillIndex], paymentMethod };
      return { bills: updatedBills };
    });
  },

  setFullPayment: (isFullPayment) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = { ...bills[activeBillIndex], isFullPayment };
      return { bills: updatedBills };
    });
  },

  setAmountTendered: (amountTenderedPaise) => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = {
        ...bills[activeBillIndex],
        amountTenderedPaise: Math.max(0, amountTenderedPaise),
      };
      return { bills: updatedBills };
    });
  },

  clearCurrentBill: () => {
    set((state) => {
      const { bills, activeBillIndex } = state;
      const updatedBills = [...bills];
      updatedBills[activeBillIndex] = createEmptyBill(activeBillIndex);
      return { bills: updatedBills };
    });
  },
}));

// Derived calculations selector
export function computeBillTotals(bill) {
  if (!bill || !bill.items) {
    return {
      subtotalPaise: 0,
      discountPaise: 0,
      taxPaise: 0,
      totalPaise: 0,
      balancePaise: 0,
      isChange: true,
      totalQty: 0,
    };
  }

  let subtotalPaise = 0;
  let taxPaise = 0;
  let totalQty = 0;

  for (const it of bill.items) {
    totalQty += it.quantity;
    const lineTotal = (it.unitPricePaise * it.quantity) - (it.discountPaise || 0);
    subtotalPaise += lineTotal;
    const tax = Math.round(lineTotal * ((it.taxRate || 0) / 100));
    taxPaise += tax;
  }

  let discountPaise = 0;
  if (bill.discountType === 'PERCENT') {
    discountPaise = Math.round(subtotalPaise * (bill.discountValue / 100));
  } else {
    discountPaise = Math.round(bill.discountValue * 100);
  }
  discountPaise = Math.min(subtotalPaise, Math.max(0, discountPaise));

  const totalPaise = Math.max(0, subtotalPaise - discountPaise);

  const tenderedPaise = bill.isFullPayment ? totalPaise : (bill.amountTenderedPaise || 0);
  const diff = tenderedPaise - totalPaise;
  const isChange = diff >= 0;
  const balancePaise = Math.abs(diff);

  return {
    subtotalPaise,
    discountPaise,
    taxPaise,
    totalPaise,
    tenderedPaise,
    balancePaise,
    isChange,
    totalQty,
  };
}
