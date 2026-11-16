/**
 * Central UI strings – single source of truth for all user-facing text.
 *
 * Import this module anywhere:
 *   import { strings } from '../../lib/strings';
 *
 * Use `strings.common.*` for shared wording (actions, labels, states, table
 * defaults, confirm/toast copy) and the feature namespaces for page-specific
 * copy. Parameterised strings are functions.
 */

import common from './strings/common';
import layout from './strings/layout';
import auth from './strings/auth';
import pos from './strings/pos';
import bills from './strings/bills';
import customers from './strings/customers';
import products from './strings/products';
import categories from './strings/categories';
import reports from './strings/reports';
import salesReports from './strings/salesReports';
import productFlow from './strings/productFlow';
import settings from './strings/settings';
import receipt from './strings/receipt';

export const strings = {
  common,
  layout,
  auth,
  pos,
  bills,
  customers,
  products,
  categories,
  reports,
  salesReports,
  productFlow,
  settings,
  receipt,
  stockLedger: categories.stockLedger,
};

export default strings;
