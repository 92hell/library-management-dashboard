import { useNavigate } from 'react-router-dom';
import { Badge, Button, ListGroup, ListGroupItem } from 'reactstrap';
import { api } from '../api';
import { ErrorAlert, Loading } from '../components/Feedback';
import Pager from '../components/Pager';
import { useAction, usePagedList } from '../hooks';
import { locale, useLanguage, useT } from '../i18n';
import { useNotifications } from '../notifications';

const timeFormats = new Map();

/** Date and time of a notification, in the current language. */
function timeFormat(lang) {
  if (!timeFormats.has(lang)) {
    timeFormats.set(lang, new Intl.DateTimeFormat(locale(lang), { dateStyle: 'medium', timeStyle: 'short' }));
  }
  return timeFormats.get(lang);
}

export default function Notifications() {
  const list = usePagedList('/api/notifications', {});
  const { refresh } = useNotifications();
  const action = useAction();
  const navigate = useNavigate();
  const lang = useLanguage();
  const t = useT();

  async function markAllRead() {
    if (await action.run(() => api.post('/api/notifications/read-all'))) {
      list.reload();
      refresh();
    }
  }

  async function open(notification) {
    if (!notification.read) {
      await action.run(() => api.post(`/api/notifications/${notification.id}/read`));
      refresh();
    }
    if (notification.link) navigate(notification.link);
    else list.reload();
  }

  if (list.error) return <ErrorAlert error={list.error} />;
  if (!list.data) return <Loading />;
  const hasUnread = list.data.content.some((n) => !n.read);

  return (
    <>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 mb-0">{t('Notifications')}</h1>
        <Button color="secondary" outline size="sm" onClick={markAllRead} disabled={!hasUnread || action.pending}>
          {t('Mark all as read')}
        </Button>
      </div>
      <ErrorAlert error={action.error} onDismiss={action.clearError} />
      {list.data.content.length === 0 ? (
        <p className="text-muted py-4">{t('You have no notifications.')}</p>
      ) : (
        <ListGroup className="shadow-sm mb-3">
          {list.data.content.map((n) => (
            <ListGroupItem key={n.id} action tag="button" onClick={() => open(n)} className={n.read ? '' : 'fw-semibold'}>
              <div className="d-flex justify-content-between gap-3">
                <span>
                  {!n.read && <Badge color="primary" pill className="me-2">{t('New')}</Badge>}
                  {n.message}
                </span>
                <span className="small text-muted text-nowrap">{timeFormat(lang).format(new Date(n.createdAt))}</span>
              </div>
            </ListGroupItem>
          ))}
        </ListGroup>
      )}
      <Pager page={list.page} totalPages={list.data.totalPages} onPageChange={list.setPage} />
    </>
  );
}
