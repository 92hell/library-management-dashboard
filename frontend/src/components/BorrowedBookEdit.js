import React, { Component } from 'react';
import { Link, withRouter } from 'react-router-dom';
import {
    Button,
    Container,
    Form,
    FormGroup,
    Input,
    Label
} from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';

class BorrowedBookEdit extends Component {

    emptyItem = {
        book: null,
        member: null,
        borrowDate: new Date().toISOString().split('T')[0],
        returnDate: null
    };

    constructor(props) {
        super(props);
        this.state = {
            item: { ...this.emptyItem, id: null },
            availableBooks: [],
            availableMembers: [],
            isLoadingBooks: true,
            isLoadingMembers: true
        };
        this.handleChange = this.handleChange.bind(this);
        this.handleBookSelectChange = this.handleBookSelectChange.bind(this);
        this.handleMemberSelectChange = this.handleMemberSelectChange.bind(this);
        this.handleSubmit = this.handleSubmit.bind(this);
    }

    async componentDidMount() {
        const { id } = this.props.match.params;

        try {
            const booksResponse = await fetch('/borrowed-books/books/active');
            if (booksResponse.ok) {
                const booksData = await booksResponse.json();
                this.setState({ availableBooks: booksData, isLoadingBooks: false });
            } else {
                console.error("Failed to fetch books:", booksResponse.status);
                this.setState({ isLoadingBooks: false });
            }
        } catch (error) {
            console.error("Error fetching books:", error);
            this.setState({ isLoadingBooks: false });
        }

        try {
            const membersResponse = await fetch('/borrowed-books/members/active');
            if (membersResponse.ok) {
                const membersData = await membersResponse.json();
                this.setState({ availableMembers: membersData, isLoadingMembers: false });
            } else {
                console.error("Failed to fetch members:", membersResponse.status);
                this.setState({ isLoadingMembers: false });
            }
        } catch (error) {
            console.error("Error fetching members:", error);
            this.setState({ isLoadingMembers: false });
        }

        if (id !== 'new') {
            try {
                const borrowedBookResponse = await fetch(`/borrowed-books/${id}`);
                if (!borrowedBookResponse.ok) {
                    throw new Error(`Failed to fetch borrowed book: ${borrowedBookResponse.status}`);
                }
                const borrowedBookData = await borrowedBookResponse.json();

                if (borrowedBookData.borrowDate) {
                    borrowedBookData.borrowDate = new Date(borrowedBookData.borrowDate).toISOString().split('T')[0];
                }
                if (borrowedBookData.returnDate) {
                    borrowedBookData.returnDate = new Date(borrowedBookData.returnDate).toISOString().split('T')[0];
                } else {
                    borrowedBookData.returnDate = '';
                }

                this.setState({ item: { ...borrowedBookData, id: parseInt(id) } });
            } catch (error) {
                console.error("Error fetching borrowed book record:", error);
            }
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

    handleBookSelectChange(event) {
        const selectedBookId = event.target.value;
        const selectedBook = this.state.availableBooks.find(book =>
            book.id === parseInt(selectedBookId)
        );
        let item = { ...this.state.item };
        item.book = selectedBook;
        this.setState({ item });
    }

    handleMemberSelectChange(event) {
        const selectedMemberId = event.target.value;
        const selectedMember = this.state.availableMembers.find(member =>
            member.id === parseInt(selectedMemberId)
        );
        let item = { ...this.state.item };
        item.member = selectedMember;
        this.setState({ item });
    }

    async handleSubmit(event) {
        event.preventDefault();
        const { item } = this.state;

        if (!item.book || item.book.id === undefined) {
            alert('Please select a book.');
            return;
        }
        if (!item.member || item.member.id === undefined) {
            alert('Please select a member.');
            return;
        }
        if (!item.borrowDate) {
            alert('Please select a borrow date.');
            return;
        }

        const itemToSave = {
            bookId: item.book.id,
            memberId: item.member.id,
            borrowDate: item.borrowDate,
            returnDate: item.returnDate === '' ? null : item.returnDate
        };

        try {
            await fetch('/borrowed-books' + (item.id ? '/' + item.id : ''), {
                method: (item.id) ? 'PUT' : 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(itemToSave),
            });
            this.props.history.push('/borrowed-books');
        } catch (error) {
            console.error("Error saving borrowed book record:", error);
        }
    }

    render() {
        const { item, availableBooks, availableMembers, isLoadingBooks, isLoadingMembers } = this.state;
        const title = <h2>{item.id ? 'Edit Borrowed Book' : 'Borrow New Book'}</h2>;

        return (
            <div>
                <AppNavbar/>
                <Container className="my-4">
                    {title}
                    <Form onSubmit={this.handleSubmit} className="p-4 border rounded shadow-sm">
                        <FormGroup>
                            <Label for="book">Book</Label>
                            {isLoadingBooks ? (
                                <p className="text-muted">Loading books...</p>
                            ) : (
                                <Input type="select" name="book" id="book"
                                       value={item.book ? item.book.id : ''}
                                       onChange={this.handleBookSelectChange} required>
                                    <option value="">Select a Book</option>
                                    {availableBooks.map(book => (
                                        <option key={book.id} value={book.id}>{book.title} (by {book.author ? book.author.name : 'N/A'})</option>
                                    ))}
                                </Input>
                            )}
                        </FormGroup>
                        <FormGroup>
                            <Label for="member">Member</Label>
                            {isLoadingMembers ? (
                                <p className="text-muted">Loading members...</p>
                            ) : (
                                <Input type="select" name="member" id="member"
                                       value={item.member ? item.member.id : ''}
                                       onChange={this.handleMemberSelectChange} required>
                                    <option value="">Select a Member</option>
                                    {availableMembers.map(member => (
                                        <option key={member.id} value={member.id}>{member.name}</option>
                                    ))}
                                </Input>
                            )}
                        </FormGroup>
                        <FormGroup>
                            <Label for="borrowDate">Borrow Date</Label>
                            <Input type="date" name="borrowDate" id="borrowDate" value={item.borrowDate || ''}
                                   onChange={this.handleChange} required />
                        </FormGroup>
                        <FormGroup>
                            <Label for="returnDate">Return Date (Optional)</Label>
                            <Input type="date" name="returnDate" id="returnDate" value={item.returnDate || ''}
                                   onChange={this.handleChange} />
                        </FormGroup>
                        <FormGroup className="mt-4">
                            <Button color="primary" type="submit">Save</Button>{' '}
                            <Button color="secondary" tag={Link} to="/borrowed-books">Cancel</Button>
                        </FormGroup>
                    </Form>
                </Container>
            </div>
        );
    }
}
export default withRouter(BorrowedBookEdit);