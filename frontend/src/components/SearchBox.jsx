import { Input, Label } from 'reactstrap';

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
