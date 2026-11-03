import { msg } from '../i18n';

/** History entries (loan event types) as shown on receipts. */
export const EVENT_LABELS = {
  IMPORTED: msg('Earlier record'),
  REQUESTED: msg('Requested online'),
  LENT: msg('Lent at the desk'),
  APPROVED: msg('Approved, copy set aside'),
  WAITLISTED: msg('Joined the queue'),
  READY: msg('Copy set aside'),
  COLLECTED: msg('Collected'),
  RENEWED: msg('Renewed'),
  RETURNED: msg('Book returned'),
  REJECTED: msg('Request declined'),
  CANCELLED: msg('Request cancelled'),
  EXPIRED: msg('Not collected in time'),
  EDITED: msg('Details corrected'),
  DELETED: msg('Record deleted'),
  FINE_CHARGED: msg('Late fee charged'),
  FINE_PAID: msg('Late fee paid'),
  FINE_WAIVED: msg('Late fee waived'),
};

export const STATUS_LABELS = {
  PENDING: msg('Requested'),
  WAITLISTED: msg('In queue'),
  READY: msg('Ready for pickup'),
  ACTIVE: msg('On loan'),
  RETURNED: msg('Returned'),
  REJECTED: msg('Declined'),
  CANCELLED: msg('Cancelled'),
  EXPIRED: msg('Not collected'),
};

export const FINE_LABELS = { UNPAID: msg('Unpaid'), PAID: msg('Paid'), WAIVED: msg('Waived') };
