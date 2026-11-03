import { LANGUAGES, setLanguage, useLanguage, useT } from '../i18n';

/** English / Bahasa Indonesia toggle; each option is labelled in its own language. */
export default function LanguageSwitch({ className = '', dark = false }) {
  const lang = useLanguage();
  const t = useT();
  return (
    <div className={`btn-group btn-group-sm ${className}`} role="group" aria-label={t('Language')}>
      {LANGUAGES.map(({ code, label }) => (
        <button
          key={code}
          type="button"
          lang={code}
          className={`btn ${lang === code ? (dark ? 'btn-light' : 'btn-secondary') : dark ? 'btn-outline-light' : 'btn-outline-secondary'}`}
          aria-pressed={lang === code}
          onClick={() => setLanguage(code)}
        >
          {code === 'en' ? 'EN' : 'ID'}
          <span className="visually-hidden"> {label}</span>
        </button>
      ))}
    </div>
  );
}
