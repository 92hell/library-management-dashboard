import { FormFeedback, FormGroup, FormText, Input, Label } from 'reactstrap';

/**
 * A labelled input with its validation message and optional hint. The message and hint are linked
 * to the input (aria-describedby; reactstrap sets aria-invalid) so screen readers read them with the field.
 * Extra props go to the Input.
 */
export default function Field({ label, name, error, hint, children, ...inputProps }) {
  const errorId = `${name}-error`;
  const hintId = `${name}-hint`;
  const describedBy = error ? errorId : hint ? hintId : undefined;
  return (
    <FormGroup>
      <Label for={name}>{label}</Label>
      <Input
        id={name}
        name={name}
        invalid={Boolean(error)}
        aria-describedby={describedBy}
        {...inputProps}
      >
        {children}
      </Input>
      {error && <FormFeedback id={errorId}>{error}</FormFeedback>}
      {hint && !error && <FormText id={hintId}>{hint}</FormText>}
    </FormGroup>
  );
}
