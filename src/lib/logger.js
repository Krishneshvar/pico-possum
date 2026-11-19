/**
 * Centralized Observability Logger for Pico Possum
 *
 * Rules:
 * - DEBUG: Visible ONLY in development runtime; stripped/silenced in release builds.
 * - INFO, WARN, ERROR: Observable anytime with formatted context tags.
 */

const IS_DEV = Boolean(import.meta.env?.DEV);

function formatPrefix(level, context) {
  const time = new Date().toISOString().slice(11, 23);
  return `[${time}] [${level.toUpperCase()}] [${context}]`;
}

function sanitize(arg) {
  if (typeof arg === 'object' && arg !== null) {
    // Avoid mutating the original object
    const clean = Array.isArray(arg) ? [...arg] : { ...arg };
    const sensitiveKeys = ['password', 'currentPassword', 'newPassword', 'confirmPassword', 'token'];
    for (const key of Object.keys(clean)) {
      if (sensitiveKeys.some((s) => key.toLowerCase().includes(s.toLowerCase()))) {
        clean[key] = '[REDACTED]';
      }
    }
    return clean;
  }
  return arg;
}

export const logger = {
  debug: (context, message, ...args) => {
    if (IS_DEV) {
      console.debug(formatPrefix('debug', context), message, ...args.map(sanitize));
    }
  },

  info: (context, message, ...args) => {
    console.info(formatPrefix('info', context), message, ...args.map(sanitize));
  },

  warn: (context, message, ...args) => {
    console.warn(formatPrefix('warn', context), message, ...args.map(sanitize));
  },

  error: (context, message, ...args) => {
    console.error(formatPrefix('error', context), message, ...args.map(sanitize));
  },

  scoped: (context) => ({
    debug: (msg, ...args) => logger.debug(context, msg, ...args),
    info: (msg, ...args) => logger.info(context, msg, ...args),
    warn: (msg, ...args) => logger.warn(context, msg, ...args),
    error: (msg, ...args) => logger.error(context, msg, ...args),
  }),
};
