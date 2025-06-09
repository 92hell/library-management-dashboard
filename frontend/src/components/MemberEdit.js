import React, { Component } from 'react';
import { Link, withRouter } from 'react-router-dom';
import {
    Button,
    Container,
    Form,
    FormGroup,
    Input,
    Label,
    ListGroup,
    ListGroupItem
} from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';

class MemberEdit extends Component {

    emptyItem = {
        name: '',
        dateOfBirth: '',
        email: '',
        phone: '',
        isActive: true
    };

    constructor(props) {
        super(props);
        this.state = {
            item: this.emptyItem,
            borrowedBooks: [],
            isLoadingBooks: true
        };
        this.handleChange = this.handleChange.bind(this);
        this.handleCheckboxChange = this.handleCheckboxChange.bind(this);
        this.handleSubmit = this.handleSubmit.bind(this);
    }

    async componentDidMount() {
        const { id } = this.props.match.params;

        if (id !== 'new') {
            try {
                const memberResponse = await fetch(`/members/${id}`);
                if (!memberResponse.ok) {
                    throw new Error(`Failed to fetch member: ${memberResponse.status}`);
                }
                const memberData = await memberResponse.json();

                if (memberData.dateOfBirth) {
                    const date = new Date(memberData.dateOfBirth);
                    if (!isNaN(date.getTime())) {
                        const year = date.getFullYear();
                        const month = String(date.getMonth() + 1).padStart(2, '0');
                        const day = String(date.getDate()).padStart(2, '0');
                        memberData.dateOfBirth = `${year}-${month}-${day}`;
                    } else {
                        console.warn("Received invalid dateOfBirth from backend:", memberData.dateOfBirth);
                        memberData.dateOfBirth = '';
                    }
                }

                this.setState({ item: memberData });

                const booksResponse = await fetch(`/members/${id}/borrowedBooks`);
                if (booksResponse.ok) {
                    const booksData = await booksResponse.json();
                    this.setState({ borrowedBooks: booksData, isLoadingBooks: false });
                } else if (booksResponse.status === 204) {
                    this.setState({ borrowedBooks: [], isLoadingBooks: false });
                } else {
                    console.error("Failed to fetch borrowed books:", booksResponse.status);
                    this.setState({ isLoadingBooks: false });
                }
            } catch (error) {
                console.error("Error fetching member or books:", error);
                this.setState({ isLoadingBooks: false });
            }
        } else {
            this.setState({ isLoadingBooks: false });
        }
    }

    handleChange(event) {
        const target = event.target;
        const value = target.value;
        const name = target.name;
        let item = { ...this.state.item };
        item[name] = value;
        this.setState({ item });
    }

    handleCheckboxChange(event) {
        const target = event.target;
        const value = target.checked; // For checkboxes, use .checked
        const name = target.name;
        let item = { ...this.state.item };
        item[name] = value;
        this.setState({ item });
    }

    async handleSubmit(event) {
        event.preventDefault();
        const { item } = this.state;

        try {
            await fetch('/members' + (item.id ? '/' + item.id : ''), {
                method: (item.id) ? 'PUT' : 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(item),
            });
            this.props.history.push('/members');
        } catch (error) {
            console.error("Error saving member:", error);
        }
    }

    render() {
        const { item, borrowedBooks, isLoadingBooks } = this.state;
        const title = <h2>{item.id ? 'Edit Member' : 'Add Member'}</h2>;

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
                            <Label for="email">Email</Label>
                            <Input type="email" name="email" id="email" value={item.email || ''}
                                   onChange={this.handleChange} autoComplete="email" required />
                        </FormGroup>
                        <FormGroup>
                            <Label for="phone">Phone</Label>
                            <Input type="tel" name="phone" id="phone" value={item.phone || ''}
                                   onChange={this.handleChange} autoComplete="tel" required maxLength="15" />
                        </FormGroup>
                        <FormGroup>
                            <Label for="dateOfBirth">Date of Birth</Label>
                            <Input type="date" name="dateOfBirth" id="dateOfBirth" value={item.dateOfBirth || ''}
                                   onChange={this.handleChange} required />
                        </FormGroup>
                        <FormGroup className="mt-4">
                            <Button color="primary" type="submit">Save</Button>{' '}
                            <Button color="secondary" tag={Link} to="/members">Cancel</Button>
                        </FormGroup>
                    </Form>

                    {item.id && (
                        <div className="mt-5">
                            <h4>Borrowed Books History</h4>
                            {isLoadingBooks ? (
                                <p className="text-muted">Loading borrowed books...</p>
                            ) : (
                                borrowedBooks.length > 0 ? (
                                    <ListGroup>
                                        {borrowedBooks.map(book => (
                                            <ListGroupItem key={book.id}>
                                                <strong>{book.title}</strong> (Published: {new Date(book.publishedDate).toLocaleDateString()})
                                            </ListGroupItem>
                                        ))}
                                    </ListGroup>
                                ) : (
                                    <p className="text-muted">No books currently borrowed by this member.</p>
                                )
                            )}
                        </div>
                    )}
                </Container>
            </div>
        );
    }
}
export default withRouter(MemberEdit);