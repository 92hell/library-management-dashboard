import React, { Component } from 'react';
import { Link, withRouter } from 'react-router-dom';
import { Button, Container, Form, FormGroup, Input, Label, ListGroup, ListGroupItem } from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';

class AuthorEdit extends Component {

    emptyItem = {
        name: '',
        dateOfBirth: ''
    };

    constructor(props) {
        super(props);
        this.state = {
            item: this.emptyItem,
            authoredBooks: []
        };
        this.handleChange = this.handleChange.bind(this);
        this.handleSubmit = this.handleSubmit.bind(this);
    }

    async componentDidMount() {
        const { id } = this.props.match.params;
        if (id !== 'new') {
            const author = await (await fetch(`/authors/${this.props.match.params.id}`)).json();

            if (author.dateOfBirth) {
                const date = new Date(author.dateOfBirth);
                if (!isNaN(date.getTime())) {
                    const year = date.getFullYear();
                    const month = String(date.getMonth() + 1).padStart(2, '0');
                    const day = String(date.getDate()).padStart(2, '0');
                    author.dateOfBirth = `${year}-${month}-${day}`;
                } else {
                    console.warn("Received invalid dateOfBirth from backend:", author.dateOfBirth);
                    author.dateOfBirth = '';
                }
            }

            this.setState({item: author});

            try {
                const booksResponse = await fetch(`/books/authoredBy/${id}`);
                if (booksResponse.ok) {
                    const booksData = await booksResponse.json();
                    this.setState({ authoredBooks: booksData });
                } else {
                    console.error("Failed to fetch books for author:", booksResponse.status);
                }
            } catch (error) {
                console.error("Error fetching books:", error);
            }
        }
    }

    handleChange(event) {
        const target = event.target;
        const value = target.value;
        const name = target.name;
        let item = {...this.state.item};
        item[name] = value;
        this.setState({item});
    }

    async handleSubmit(event) {
        event.preventDefault();
        const {item} = this.state;

        try {
            await fetch('/authors' + (item.id ? '/' + item.id : ''), {
                method: (item.id) ? 'PUT' : 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(item),
            });
            this.props.history.push('/authors');
        } catch (error) {
            console.error("Error saving author:", error);
        }
    }

    render() {
        const { item, authoredBooks } = this.state;
        const title = <h2>{item.id ? 'Edit Author' : 'Add Author'}</h2>;

        return (
            <div>
                <AppNavbar/>
                <Container className="my-4">
                    {title}
                    <Form onSubmit={this.handleSubmit} className="p-4 border rounded shadow-sm">
                        <FormGroup>
                            <Label for="name">Name</Label>
                            <Input type="text" name="name" id="name" value={item.name || ''}
                                   onChange={this.handleChange} autoComplete="name" required />
                        </FormGroup>
                        <FormGroup>
                            <Label for="dateOfBirth">Date of Birth</Label>
                            <Input type="date" name="dateOfBirth" id="dateOfBirth" value={item.dateOfBirth || ''}
                                   onChange={this.handleChange} />
                        </FormGroup>
                        <FormGroup className="mt-4">
                            <Button color="primary" type="submit">Save</Button>{' '}
                            <Button color="secondary" tag={Link} to="/authors">Cancel</Button>
                        </FormGroup>
                    </Form>
                    {item.id && (
                        <div className="mt-5">
                            <h4>Authored Books</h4>
                            {authoredBooks.length > 0 ? (
                                <ListGroup>
                                    {authoredBooks.map(book => (
                                        <ListGroupItem key={book.id}>
                                            {book.title} (Published: {new Date(book.publishedDate).toLocaleDateString()})
                                        </ListGroupItem>
                                    ))}
                                </ListGroup>
                            ) : (
                                <p className="text-muted">No books found for this author.</p>
                            )}
                        </div>
                    )}
                </Container>
            </div>
        );
    }
}
export default withRouter(AuthorEdit);