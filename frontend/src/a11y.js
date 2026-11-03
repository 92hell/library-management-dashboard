/**
 * Moves keyboard focus to the page's main heading. Use it after an action that removes the button
 * that was pressed (a row leaving the list, a form closing): otherwise focus falls back to the top
 * of the document and keyboard and screen-reader users lose their place.
 */
export function focusPageHeading() {
  const heading = document.querySelector('main h1');
  if (!heading) return;
  if (!heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1');
  heading.focus();
}
