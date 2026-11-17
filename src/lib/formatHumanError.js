/**
 * Helper to convert technical backend errors (SQLite constraints, I/O errors,
 * validation panics, CSV parse issues) into clear, human-readable explanations
 * with practical advice for store owners and cashier staff.
 */

export function formatHumanError(err, context = 'general') {
  const rawMsg =
    typeof err === 'string'
      ? err
      : err?.message || err?.toString?.() || 'Unknown error occurred';

  const lower = rawMsg.toLowerCase();

  // 1. Not a valid SQLite database / corrupt backup
  if (
    lower.includes('not a valid sqlite') ||
    lower.includes('file is not a valid sqlite') ||
    lower.includes('bytes.len() < 16') ||
    lower.includes('file is too short') ||
    lower.includes('file is encrypted')
  ) {
    return {
      title: 'Invalid Backup File',
      message:
        'The selected file is not a valid database backup or is empty.',
      hint: 'Please ensure you select a database backup file ending in ".db" created by PicoPOSSUM or the previous Java POS application.',
      technical: rawMsg,
    };
  }

  // 2. File not found / missing path
  if (
    lower.includes('no such file') ||
    lower.includes('not found at') ||
    lower.includes('does not exist') ||
    lower.includes('os error 2')
  ) {
    return {
      title: 'File Not Found',
      message:
        'The selected file could not be found at the specified location.',
      hint: 'The file might have been moved, renamed, or deleted. Please select the file again from your computer.',
      technical: rawMsg,
    };
  }

  // 3. Permission denied / file access issues
  if (
    lower.includes('permission denied') ||
    lower.includes('access is denied') ||
    lower.includes('os error 13') ||
    lower.includes('os error 5')
  ) {
    return {
      title: 'File Access Denied',
      message:
        'The app does not have system permission to read or write this file.',
      hint: 'Try moving the file to your standard Documents or Desktop folder, or check that your user account has read permissions for the file.',
      technical: rawMsg,
    };
  }

  // 4. Database busy / locked
  if (
    lower.includes('database is locked') ||
    lower.includes('busy timeout') ||
    lower.includes('database table is locked')
  ) {
    return {
      title: 'Database In Use',
      message:
        'The application database is currently busy with another operation.',
      hint: 'Please wait a few seconds and try again. If the issue persists, restart the application and retry.',
      technical: rawMsg,
    };
  }

  // 5. Unique constraint conflict (duplicate SKU, invoice, category, customer)
  if (lower.includes('unique constraint failed')) {
    if (lower.includes('products.sku')) {
      return {
        title: 'Duplicate SKU Code',
        message:
          'One or more items in the file have an SKU code that is already in use by another product.',
        hint: 'Every product must have a unique SKU code. Please update the duplicate SKU codes in your file before re-importing.',
        technical: rawMsg,
      };
    }
    if (lower.includes('categories.name')) {
      return {
        title: 'Duplicate Category Name',
        message: 'A category with this name already exists in your inventory.',
        hint: 'Category names must be unique. Change the category name or edit the existing category instead.',
        technical: rawMsg,
      };
    }
    if (lower.includes('sales.invoice_number') || lower.includes('legacy_sales.invoice_number')) {
      return {
        title: 'Duplicate Bill Number',
        message: 'An invoice with this bill number has already been recorded.',
        hint: 'Invoice numbers cannot be reused. Verify that you are not importing the same sales file twice.',
        technical: rawMsg,
      };
    }
    if (lower.includes('users.username')) {
      return {
        title: 'Username Already Taken',
        message: 'A user account with this username already exists.',
        hint: 'Please choose a different username.',
        technical: rawMsg,
      };
    }
    return {
      title: 'Duplicate Record Conflict',
      message: 'A record with the same unique identifier already exists.',
      hint: 'Please check your file to ensure records do not conflict with existing database items.',
      technical: rawMsg,
    };
  }

  // 6. Missing required columns
  if (lower.includes('missing required column') || lower.includes('missing field') || lower.includes('header')) {
    return {
      title: 'Missing Required Columns',
      message:
        'The uploaded spreadsheet is missing one or more required column headers.',
      hint: 'Review the "Supported Fields" guide above to verify that all required column names (such as Product Name or Price) are included in row 1.',
      technical: rawMsg,
    };
  }

  // 7. Malformed CSV / column mismatch
  if (
    lower.includes('found') && lower.includes('columns instead of') ||
    lower.includes('uneven columns') ||
    lower.includes('record on line') ||
    lower.includes('csv parse')
  ) {
    return {
      title: 'Spreadsheet Format Issue',
      message:
        'One or more rows have uneven columns or broken quotation marks.',
      hint: 'If your descriptions contain commas, make sure they are enclosed in double quotes. Re-saving your file as CSV UTF-8 from Excel often resolves this.',
      technical: rawMsg,
    };
  }

  // 8. Number or date parse failure
  if (
    lower.includes('cannot parse float') ||
    lower.includes('invalid digit') ||
    lower.includes('cannot parse integer') ||
    lower.includes('invalid number')
  ) {
    return {
      title: 'Invalid Number Format',
      message:
        'A value expected to be a number (such as price, quantity, or tax) could not be parsed.',
      hint: 'Make sure price columns contain only plain numbers without currency symbols (e.g. 120.50 instead of $120.50) and quantities are whole numbers.',
      technical: rawMsg,
    };
  }

  if (lower.includes('invalid date') || lower.includes('parse date')) {
    return {
      title: 'Invalid Date Format',
      message: 'A date in the file could not be recognized.',
      hint: 'Please format dates as YYYY-MM-DD (e.g. 2026-11-15) or standard DD/MM/YYYY.',
      technical: rawMsg,
    };
  }

  // 9. Empty sheet or file
  if (lower.includes('empty') || lower.includes('no sheets') || lower.includes('contains no rows')) {
    return {
      title: 'Empty File',
      message: 'The selected file contains no readable records or rows.',
      hint: 'Please verify that the file contains column headers in the first row followed by at least one row of data.',
      technical: rawMsg,
    };
  }

  // 10. Unsupported file type
  if (lower.includes('unsupported file type') || lower.includes('extension')) {
    return {
      title: 'Unsupported File Type',
      message: 'The selected file format is not supported for this action.',
      hint: context === 'restore'
        ? 'Please select a database file ending in ".db".'
        : 'Please select a CSV (.csv) or Excel spreadsheet (.xlsx, .xls).',
      technical: rawMsg,
    };
  }

  // 11. Contextual default fallbacks
  if (context === 'restore') {
    return {
      title: 'Backup Restore Failed',
      message:
        'The application could not restore the database from the selected backup file.',
      hint: 'Verify that the backup file is not open in another tool and is a valid SQLite database.',
      technical: rawMsg,
    };
  }

  if (context === 'migration') {
    return {
      title: 'Data Migration Failed',
      message:
        'The application encountered an issue while migrating data from the previous database.',
      hint: 'Ensure the previous database is not corrupted and has all standard tables intact.',
      technical: rawMsg,
    };
  }

  return {
    title: 'Operation Failed',
    message: rawMsg,
    hint: 'Review your file or try again. If the problem continues, inspect the technical error details below.',
    technical: rawMsg,
  };
}

/**
 * Converts row-level import errors into concise readable phrases.
 */
export function formatRowError(errStr) {
  if (!errStr || typeof errStr !== 'string') return '';
  const lower = errStr.toLowerCase();

  if (lower.includes('unique constraint failed: products.sku')) {
    return errStr.replace(/unique constraint failed: products\.sku/i, 'duplicate SKU code');
  }
  if (lower.includes('unique constraint failed: categories.name')) {
    return errStr.replace(/unique constraint failed: categories\.name/i, 'duplicate category name');
  }
  if (lower.includes('not null constraint failed: products.name')) {
    return errStr.replace(/not null constraint failed: products\.name/i, 'missing product name');
  }
  if (lower.includes('cannot parse float')) {
    return errStr.replace(/cannot parse float:? ?/i, 'invalid price format: ');
  }
  return errStr;
}
