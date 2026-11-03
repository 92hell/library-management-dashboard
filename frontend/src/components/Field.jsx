import { FormFeedback, FormGroup, FormText, Input, Label } from 'reactstrap';

/** A labelled input with its validation message and optional hint. Extra props go to the Input. */
export default function Field({ label, name, error, hint, children, ...inputProps }) {
  return (
    <FormGroup>
      <Label for={name}>{label}</Label>
      <Input id={name} name={name} invalid={Boolean(error)} {...inputProps}>
        {children}
      </Input>
      {error && <FormFeedback>{error}</FormFeedback>}
      {hint && !error && <FormText>{hint}</FormText>}
    </FormGroup>
  );
}
