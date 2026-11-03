import { Input, Label } from 'reactstrap';

/** A search input with a visually hidden label; pass `label` and `placeholder` already translated. */
export default function SearchBox({ id, label, value, onChange, placeholder }) {
  return (
    <>
      <Label for={id} className="visually-hidden">
        {label}
      </Label>
      <Input
        id={id}
        type="search"
        placeholder={placeholder ?? label}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete="off"
      />
    </>
  );
}
