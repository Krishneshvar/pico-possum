import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Save } from 'lucide-react';
import { tauriApi } from '../../../lib/tauri';
import { toast } from '../../../stores/toast.store';
import { confirmDialog } from '../../../stores/confirm.store';
import { formatPaise } from '../../../lib/formatters';
import { computeBillTotals } from '../../../stores/pos.store';
import { strings } from '../../../lib/strings';
import { validateUnitPriceAgainstMrp } from '../../../lib/validation';
import { logger } from '../../../lib/logger';

const b = strings.bills;

export function useBillEditDraft({
  saleDetails,
  saleId,
  catalogProducts = [],
  generalSettings,
  stockAllowanceByProduct,
  productStockById,
  allowNegativeStock,
  searchInputRef,
  amountTenderedInputRef,
  editRequest,
}) {
  const queryClient = useQueryClient();
  const handledEditRequest = useRef(null);

  const [editing, setEditing] = useState(false);
  const [editError, setEditError] = useState('');
  const [draftBill, setDraftBill] = useState(null);
  const [customerModalOpen, setCustomerModalOpen] = useState(false);
  const [editSearchQuery, setEditSearchQuery] = useState('');
  const [editSelectedCategory, setEditSelectedCategory] = useState(null);
  const [editSearchFocused, setEditSearchFocused] = useState(false);

  const editProducts = useMemo(() => {
    const q = editSearchQuery.trim().toLowerCase();
    return catalogProducts.filter((p) => {
      if (editSelectedCategory && (p.categoryId ?? p.category_id) !== editSelectedCategory) return false;
      if (!q) return true;
      return (
        p.name.toLowerCase().includes(q) ||
        (p.sku && p.sku.toLowerCase().includes(q))
      );
    });
  }, [catalogProducts, editSelectedCategory, editSearchQuery]);

  const seedDraft = useCallback(
    (details) => {
      if (!details) return;
      const { sale, items } = details;
      setDraftBill({
        items: items.map((it) => {
          const productId = it.productId ?? it.product_id;
          const product = catalogProducts.find((p) => p.id === productId);
          const unitPrice = it.unitPricePaise ?? it.unit_price_paise;
          return {
            productId,
            name: it.productName ?? it.product_name,
            sku: it.productSku ?? it.product_sku ?? product?.sku ?? '',
            unitPricePaise: unitPrice,
            mrpPaise: product?.mrpPaise ?? product?.mrp_paise ?? unitPrice,
            costPricePaise: product?.costPricePaise ?? product?.cost_price_paise ?? it.costPricePaise ?? it.cost_price_paise ?? 0,
            taxRate: it.taxRate ?? it.tax_rate ?? 0,
            quantity: it.quantity,
            discountPaise: it.discountPaise ?? it.discount_paise ?? 0,
          };
        }),
        customer:
          (sale.customerId ?? sale.customer_id) || (sale.customerName ?? sale.customer_name)
            ? {
                id: (sale.customerId ?? sale.customer_id) || null,
                name: (sale.customerName ?? sale.customer_name) || '',
                phone: (sale.customerPhone ?? sale.customer_phone) || '',
                address: (sale.customerAddress ?? sale.customer_address) || '',
              }
            : null,
        discountType: 'FIXED',
        discountValue: ((sale.discountPaise ?? sale.discount_paise) || 0) / 100,
        paymentMethod: (sale.paymentMethod ?? sale.payment_method) || 'CASH',
        isFullPayment: (sale.paidPaise ?? sale.paid_paise) === (sale.totalPaise ?? sale.total_paise),
        amountTenderedPaise: (sale.paidPaise ?? sale.paid_paise) || 0,
        notes: sale.notes || '',
      });
    },
    [catalogProducts]
  );

  const enterEdit = () => {
    seedDraft(saleDetails);
    setEditError('');
    setEditSearchQuery('');
    setEditSelectedCategory(null);
    setEditing(true);
  };

  useEffect(() => {
    if (!editRequest || !saleDetails) return;
    if (handledEditRequest.current === editRequest) return;

    handledEditRequest.current = editRequest;
    seedDraft(saleDetails);
    setEditError('');
    setEditSearchQuery('');
    setEditSelectedCategory(null);
    setEditing(true);
  }, [editRequest, saleDetails, seedDraft]);

  useEffect(() => {
    if (!editing) return;
    const handleKeyDown = (e) => {
      const isCmd = e.ctrlKey || e.metaKey;
      if (isCmd && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        searchInputRef.current?.focus();
        searchInputRef.current?.select();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [editing, searchInputRef]);

  const cancelEdit = () => {
    setEditing(false);
    setEditError('');
    setDraftBill(null);
    setCustomerModalOpen(false);
    setEditSearchQuery('');
    setEditSelectedCategory(null);
  };

  const draftTotals = useMemo(() => computeBillTotals(draftBill), [draftBill]);

  const isDraftPaymentValid = useMemo(() => {
    if (!draftBill) return false;
    const requiresTender =
      !draftBill.paymentMethod ||
      draftBill.paymentMethod === 'CASH' ||
      draftBill.paymentMethod === 'GIFT_CARD';
    if (!requiresTender) return true;
    const isFull = draftBill.isFullPayment !== false;
    const tendered = draftBill.amountTenderedPaise || 0;
    const total = draftTotals.totalPaise;
    return isFull ? total === 0 || tendered >= total : total > 0 && tendered > 0 && tendered < total;
  }, [draftBill, draftTotals.totalPaise]);

  const addProductToDraft = (product) => {
    if (!allowNegativeStock) {
      const available =
        (productStockById.get(product.id)?.current_stock ?? product.current_stock ?? 0) +
        (stockAllowanceByProduct.get(product.id) || 0);
      const inCart = draftBill?.items.find((it) => it.productId === product.id)?.quantity || 0;

      if (available <= 0) {
        toast.warning(b.toasts.outOfStock(product.name));
        return;
      }
      if (inCart + 1 > available) {
        toast.warning(
          b.toasts.stockLimit(available, product.name),
          { title: b.toasts.stockLimitTitle }
        );
        return;
      }
    }

    setDraftBill((prev) => {
      if (!prev) return prev;
      const existing = prev.items.find((it) => it.productId === product.id);
      const items = existing
        ? prev.items.map((it) =>
            it.productId === product.id ? { ...it, quantity: it.quantity + 1 } : it
          )
        : [
            ...prev.items,
            {
              productId: product.id,
              name: product.name,
              sku: product.sku || '',
              unitPricePaise: (product.mrpPaise ?? product.mrp_paise) || 0,
              mrpPaise: (product.mrpPaise ?? product.mrp_paise) || 0,
              costPricePaise: (product.costPricePaise ?? product.cost_price_paise) || 0,
              taxRate: (product.taxRate ?? product.tax_rate) || 0,
              quantity: 1,
              discountPaise: 0,
            },
          ];
      return { ...prev, items };
    });
  };

  const updateDraftItemQty = (productId, quantity) => {
    if (quantity <= 0) {
      setDraftBill((prev) =>
        prev ? { ...prev, items: prev.items.filter((it) => it.productId !== productId) } : prev
      );
      return;
    }

    if (!allowNegativeStock) {
      const stock = productStockById.get(productId)?.currentStock ?? productStockById.get(productId)?.current_stock ?? 0;
      const available = stock + (stockAllowanceByProduct.get(productId) || 0);
      if (quantity > available) {
        const name = draftBill?.items.find((it) => it.productId === productId)?.name || b.labels.itemFallback;
        toast.warning(
          b.toasts.stockLimit(available, name),
          { title: b.toasts.stockLimitTitle }
        );
        return;
      }
    }

    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            items: prev.items.map((it) =>
              it.productId === productId ? { ...it, quantity } : it
            ),
          }
        : prev
    );
  };

  const updateDraftItemPrice = (productId, unitPricePaise) => {
    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            items: prev.items.map((it) => {
              if (it.productId !== productId) return it;
              const mrp = it.mrpPaise ?? it.unitPricePaise;
              const capped =
                mrp != null
                  ? Math.min(Math.max(0, unitPricePaise || 0), mrp)
                  : Math.max(0, unitPricePaise || 0);
              return { ...it, unitPricePaise: capped };
            }),
          }
        : prev
    );
  };

  const updateDraftItemDiscount = (productId, discountPaise) => {
    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            items: prev.items.map((it) =>
              it.productId === productId
                ? { ...it, discountPaise: Math.max(0, discountPaise || 0) }
                : it
            ),
          }
        : prev
    );
  };

  const removeDraftItem = (productId) => {
    setDraftBill((prev) =>
      prev ? { ...prev, items: prev.items.filter((it) => it.productId !== productId) } : prev
    );
  };

  const setDraftCustomer = (customer) => {
    setDraftBill((prev) => (prev ? { ...prev, customer } : prev));
  };

  const setDraftDiscount = (discountType, discountValue) => {
    setDraftBill((prev) =>
      prev
        ? {
            ...prev,
            discountType,
            discountValue: Math.max(0, Number(discountValue) || 0),
          }
        : prev
    );
  };

  const setDraftPaymentMethod = (paymentMethod) => {
    setDraftBill((prev) => (prev ? { ...prev, paymentMethod } : prev));
  };

  const setDraftFullPayment = (isFullPayment) => {
    setDraftBill((prev) => (prev ? { ...prev, isFullPayment } : prev));
  };

  const setDraftAmountTendered = (amountTenderedPaise) => {
    setDraftBill((prev) =>
      prev ? { ...prev, amountTenderedPaise: Math.max(0, amountTenderedPaise || 0) } : prev
    );
  };

  const setDraftNotes = (notes) => {
    setDraftBill((prev) => (prev ? { ...prev, notes } : prev));
  };

  const invalidateSalesAndAnalytics = () => {
    queryClient.invalidateQueries({ queryKey: ['sales'] });
    queryClient.invalidateQueries({ queryKey: ['saleDetails'] });
    queryClient.invalidateQueries({ queryKey: ['products'] });
    queryClient.invalidateQueries({ queryKey: ['stockMovements'] });
    queryClient.invalidateQueries({ queryKey: ['productFlow'] });
    queryClient.invalidateQueries({ queryKey: ['salesReportSummary'] });
    queryClient.invalidateQueries({ queryKey: ['topProducts'] });
    queryClient.invalidateQueries({ queryKey: ['topCategories'] });
    queryClient.invalidateQueries({ queryKey: ['multiYearSales'] });
    queryClient.invalidateQueries({ queryKey: ['salesBreakdown'] });
    queryClient.invalidateQueries({ queryKey: ['customers'] });
  };

  const updateMutation = useMutation({
    mutationFn: (payload) => tauriApi.updateSale(payload),
    onSuccess: () => {
      invalidateSalesAndAnalytics();
      setEditing(false);
      setEditError('');
      setDraftBill(null);
      setCustomerModalOpen(false);
      setEditSearchQuery('');
      setEditSelectedCategory(null);
      const invNo = saleDetails?.sale?.invoiceNumber ?? saleDetails?.sale?.invoice_number;
      logger.info('Bills', `Bill amended successfully: ${invNo}`);
      toast.success(b.toasts.amended);
    },
    onError: (err) => {
      logger.error('Bills', 'Failed to amend bill:', err);
      const message = b.toasts.saveFailed(String(err));
      setEditError(message);
      toast.error(message, { title: b.toasts.saveFailedTitle });
    },
  });

  const handleSaveEdit = async () => {
    setEditError('');
    if (!draftBill || draftBill.items.length === 0) {
      const message = b.validation.emptyBill;
      setEditError(message);
      toast.warning(message);
      return;
    }

    const requiresTender =
      !draftBill.paymentMethod ||
      draftBill.paymentMethod === 'CASH' ||
      draftBill.paymentMethod === 'GIFT_CARD';
    const isFull = draftBill.isFullPayment !== false;
    const tendered = draftBill.amountTenderedPaise || 0;
    const total = draftTotals.totalPaise;

    if (requiresTender) {
      if (isFull) {
        if (total > 0 && tendered < total) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          const message = b.validation.fullPaymentRequired(formatPaise(total));
          setEditError(message);
          toast.warning(message);
          return;
        }
      } else {
        if (tendered <= 0) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          const message = b.validation.partialPaymentRequired;
          setEditError(message);
          toast.warning(message);
          return;
        }
        if (total > 0 && tendered >= total) {
          amountTenderedInputRef.current?.focus();
          amountTenderedInputRef.current?.select();
          const message = b.validation.partialPaymentTooHigh(formatPaise(total));
          setEditError(message);
          toast.warning(message);
          return;
        }
      }
    }

    for (const item of draftBill.items) {
      const priceErr = validateUnitPriceAgainstMrp(item.unitPricePaise, item.mrpPaise, item.name);
      if (priceErr) {
        setEditError(priceErr);
        toast.warning(priceErr);
        return;
      }
    }

    const { confirmed, value: amendmentReason } = await confirmDialog({
      title: b.dialogs.amend.title,
      message: b.dialogs.amend.message(saleDetails?.sale?.invoiceNumber ?? saleDetails?.sale?.invoice_number),
      input: {
        label: b.dialogs.amend.reasonLabel,
        defaultValue: b.dialogs.amend.reasonDefault,
        placeholder: b.dialogs.amend.reasonPlaceholder,
        required: true,
      },
      confirmLabel: b.dialogs.amend.confirm,
      cancelLabel: b.dialogs.amend.cancel,
      tone: 'primary',
      icon: Save,
    });

    if (!confirmed) return;

    const payload = {
      saleId: saleDetails?.sale?.id || saleId,
      customerId: draftBill.customer?.id || null,
      customerName: draftBill.customer?.name?.trim() || null,
      customerPhone: draftBill.customer?.phone?.trim() || null,
      customerAddress: draftBill.customer?.address?.trim() || null,
      items: draftBill.items.map((it) => ({
        productId: it.productId,
        quantity: Number(it.quantity),
        unitPricePaise: Number(it.unitPricePaise),
        discountPaise: Number(it.discountPaise || 0),
      })),
      discountPaise: draftTotals.discountPaise,
      paymentMethod: draftBill.paymentMethod,
      paidPaise: requiresTender ? draftTotals.tenderedPaise : draftTotals.totalPaise,
      notes: amendmentReason
        ? `[Amended: ${amendmentReason}] ${draftBill.notes || ''}`.trim()
        : draftBill.notes || null,
    };
    updateMutation.mutate(payload);
  };

  return {
    editing,
    setEditing,
    editError,
    draftBill,
    customerModalOpen,
    setCustomerModalOpen,
    editSearchQuery,
    setEditSearchQuery,
    editSelectedCategory,
    setEditSelectedCategory,
    editSearchFocused,
    setEditSearchFocused,
    editProducts,
    draftTotals,
    isDraftPaymentValid,
    enterEdit,
    cancelEdit,
    addProductToDraft,
    updateDraftItemQty,
    updateDraftItemPrice,
    updateDraftItemDiscount,
    removeDraftItem,
    setDraftCustomer,
    setDraftDiscount,
    setDraftPaymentMethod,
    setDraftFullPayment,
    setDraftAmountTendered,
    setDraftNotes,
    handleSaveEdit,
    updateMutation,
    invalidateSalesAndAnalytics,
  };
}
