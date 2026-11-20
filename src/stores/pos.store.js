import { create } from 'zustand';
import { tauriApi } from '../lib/tauri';
import { toast } from './toast.store';
import { strings } from '../lib/strings';
import { logger } from '../lib/logger';

const createEmptyBill = (index) => ({
  index,
  items: [],
  customer: null,
  discountType: 'FIXED', // 'FIXED' or 'PERCENT'
  discountValue: 0,      // in Rupees or %
  paymentMethod: 'CASH',
  isFullPayment: true,
  amountTenderedPaise: 0,
  notes: '',
});

// Helper to persist or delete an open bill in SQLite (debounced to avoid IPC/disk thrashing during fast typing)
const syncTimers = new Map();

const syncBillToDb = (index, bill, immediate = false) => {
  if (syncTimers.has(index)) {
    clearTimeout(syncTimers.get(index));
    syncTimers.delete(index);
  }

  const persist = async () => {
    try {
      if (bill && bill.items && bill.items.length > 0) {
        await tauriApi.saveOpenBill(index, JSON.stringify(bill));
        logger.debug('POS', `Synced draft bill ${index} (${bill.items.length} items) to database`);
      } else {
        await tauriApi.deleteOpenBill(index);
        logger.debug('POS', `Cleared empty draft bill ${index} from database`);
      }
    } catch (err) {
      logger.error('POS', `Failed to sync bill ${index} to database:`, err);
    }
  };

  if (immediate) {
    persist();
  } else {
    syncTimers.set(index, setTimeout(persist, 200));
  }
};

export const usePosStore = create((set, get) => ({
  activeBillIndex: 0,
  bills: Array.from({ length: 9 }, (_, i) => createEmptyBill(i)),
  isHydrated: false,

  hydrateOpenBills: async () => {
    if (get().isHydrated) return;
    try {
      const openBills = await tauriApi.loadOpenBills();
      if (Array.isArray(openBills) && openBills.length > 0) {
        set((state) => {
          const newBills = [...state.bills];
          for (const [idx, jsonPayload] of openBills) {
            if (idx >= 0 && idx < 9) {
              try {
                const parsed = JSON.parse(jsonPayload);
                newBills[idx] = { ...createEmptyBill(idx), ...parsed, index: idx };
              } catch (e) {
                logger.warn('POS', `Could not parse open bill ${idx}:`, e);
              }
            }
          }
          logger.info('POS', `Hydrated ${openBills.length} open bill drafts from database`);
          return { bills: newBills, isHydrated: true };
        });
      } else {
        set({ isHydrated: true });
      }
    } catch (err) {
      logger.warn('POS', 'Could not hydrate open bills from database:', err);
      toast.warning(strings.pos.store.restoreFailed, {
        title: strings.pos.store.restoreFailedTitle,
      });
      set({ isHydrated: true });
    }
  },

  switchBill: (index) => {
    if (index >= 0 && index < 9) {
      set({ activeBillIndex: index });
    }
  },

  nextEmptyBill: () => {
    const { bills, activeBillIndex } = get();
    // Look for first bill with 0 items
    const emptyIdx = bills.findIndex((b) => b.items.length === 0);
    if (emptyIdx !== -1) {
      set({ activeBillIndex: emptyIdx });
      return emptyIdx;
    }
    // Otherwise cycle to next index
    const nextIdx = (activeBillIndex + 1) % 9;
    set({ activeBillIndex: nextIdx });
    return nextIdx;
  },

  getCurrentBill: () => {
    const { bills, activeBillIndex } = get();
    return bills[activeBillIndex] || createEmptyBill(activeBillIndex);
  },

  addItem: (product) => {
    const { bills, activeBillIndex } = get();
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

    const updatedBill = { ...currentBill, items: newItems };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  updateItemQty: (productId, quantity) => {
    if (quantity <= 0) {
      get().removeItem(productId);
      return;
    }
    const { bills, activeBillIndex } = get();
    const currentBill = bills[activeBillIndex];
    const newItems = currentBill.items.map((it) =>
      it.productId === productId ? { ...it, quantity } : it
    );
    const updatedBill = { ...currentBill, items: newItems };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  updateItemPrice: (productId, unitPricePaise) => {
    const { bills, activeBillIndex } = get();
    const currentBill = bills[activeBillIndex];
    const newItems = currentBill.items.map((it) => {
      if (it.productId !== productId) return it;
      const mrp = it.mrpPaise ?? it.unitPricePaise;
      const capped = mrp != null ? Math.min(Math.max(0, unitPricePaise), mrp) : Math.max(0, unitPricePaise);
      return { ...it, unitPricePaise: capped };
    });
    const updatedBill = { ...currentBill, items: newItems };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  updateItemDiscount: (productId, discountPaise) => {
    const { bills, activeBillIndex } = get();
    const currentBill = bills[activeBillIndex];
    const newItems = currentBill.items.map((it) =>
      it.productId === productId ? { ...it, discountPaise: Math.max(0, discountPaise) } : it
    );
    const updatedBill = { ...currentBill, items: newItems };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  removeItem: (productId) => {
    const { bills, activeBillIndex } = get();
    const currentBill = bills[activeBillIndex];
    const newItems = currentBill.items.filter((it) => it.productId !== productId);
    const updatedBill = { ...currentBill, items: newItems };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  setCustomer: (customer) => {
    const { bills, activeBillIndex } = get();
    const updatedBill = { ...bills[activeBillIndex], customer };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  setDiscount: (discountType, discountValue) => {
    const { bills, activeBillIndex } = get();
    const updatedBill = {
      ...bills[activeBillIndex],
      discountType,
      discountValue: Math.max(0, Number(discountValue) || 0),
    };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  setPaymentMethod: (paymentMethod) => {
    const { bills, activeBillIndex } = get();
    const updatedBill = { ...bills[activeBillIndex], paymentMethod };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  setFullPayment: (isFullPayment) => {
    const { bills, activeBillIndex } = get();
    const updatedBill = { ...bills[activeBillIndex], isFullPayment };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  setNotes: (notes) => {
    const { bills, activeBillIndex } = get();
    const updatedBill = { ...bills[activeBillIndex], notes };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  setAmountTendered: (amountTenderedPaise) => {
    const { bills, activeBillIndex } = get();
    const updatedBill = {
      ...bills[activeBillIndex],
      amountTenderedPaise: Math.max(0, amountTenderedPaise),
    };
    const updatedBills = [...bills];
    updatedBills[activeBillIndex] = updatedBill;

    set({ bills: updatedBills });
    syncBillToDb(activeBillIndex, updatedBill);
  },

  clearCurrentBill: () => {
    const { activeBillIndex } = get();
    const empty = createEmptyBill(activeBillIndex);
    set((state) => {
      const updatedBills = [...state.bills];
      updatedBills[activeBillIndex] = empty;
      return { bills: updatedBills };
    });
    syncBillToDb(activeBillIndex, empty, true);
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
      tenderedPaise: 0,
      balancePaise: 0,
      isChange: true,
      totalQty: 0,
    };
  }

  let subtotalPaise = 0;
  let taxPaise = 0;
  let totalQty = 0;

  for (const it of bill.items) {
    totalQty += (it.quantity || 0);
    const lineDisc = Math.max(0, it.discountPaise || 0);
    const lineTotal = Math.max(0, (it.unitPricePaise * it.quantity) - lineDisc);
    subtotalPaise += lineTotal;
    const tax = Math.round(lineTotal * ((it.taxRate || 0) / 100));
    taxPaise += tax;
  }

  let discountPaise = 0;
  const rawDiscVal = parseFloat(bill.discountValue);
  const cleanDiscVal = !isNaN(rawDiscVal) && rawDiscVal > 0 ? rawDiscVal : 0;
  if (bill.discountType === 'PERCENT') {
    discountPaise = Math.round(subtotalPaise * (cleanDiscVal / 100));
  } else {
    discountPaise = Math.round(cleanDiscVal * 100);
  }
  discountPaise = Math.min(subtotalPaise, Math.max(0, discountPaise));

  const totalPaise = Math.max(0, subtotalPaise - discountPaise);

  const requiresTender = !bill.paymentMethod || bill.paymentMethod === 'CASH' || bill.paymentMethod === 'GIFT_CARD';
  const tenderedPaise = requiresTender ? (bill.amountTenderedPaise || 0) : totalPaise;
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
