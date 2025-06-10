import React, { Component } from 'react';
import { Button, ButtonGroup, Container, Table, Input, FormGroup, Label, Row, Col } from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';
import { Link } from 'react-router-dom';

class BorrowedBookList extends Component {

    searchTimeout = null;
    searchInputRef = React.createRef();
    borrowDateInputRef = React.createRef();

    constructor(props) {
        super(props);
        this.state = {
            borrowedBooks: [],
            isLoading: true,
            generalSearchTerm: '',
            borrowDateFilter: ''
        };
        this.remove = this.remove.bind(this);
        this.fetchBorrowedBooks = this.fetchBorrowedBooks.bind(this);
        this.handleGeneralSearchChange = this.handleGeneralSearchChange.bind(this);
        this.handleBorrowDateFilterChange = this.handleBorrowDateFilterChange.bind(this);
        this.handleKeyPress = this.handleKeyPress.bind(this);
    }

    componentDidMount() {
        this.fetchBorrowedBooks(this.state.generalSearchTerm, this.state.borrowDateFilter);
        if (this.searchInputRef.current) {
            this.searchInputRef.focus();
        }
    }

    componentWillUnmount() {
        if (this.searchTimeout) {
            clearTimeout(this.searchTimeout);
        }
    }

    async fetchBorrowedBooks(generalSearch = '', borrowDate = '') {
        this.setState({ isLoading: true });

        const queryParams = [];
        if (generalSearch) {
            queryParams.push(`searchTerm=${encodeURIComponent(generalSearch)}`);
        }
        if (borrowDate) {
            queryParams.push(`borrowDateFilter=${encodeURIComponent(borrowDate)}`);
        }

        const url = `/borrowed-books` + (queryParams.length > 0 ? `?${queryParams.join('&')}` : '');

        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            this.setState({ borrowedBooks: data, isLoading: false });
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        } catch (error) {
            console.error("Error fetching borrowed books:", error);
            this.setState({ isLoading: false });
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        }
    }

    handleGeneralSearchChange(event) {
        const newSearchTerm = event.target.value;
        this.setState({ generalSearchTerm: newSearchTerm });

        if (this.searchTimeout) {
            clearTimeout(this.searchTimeout);
        }

        this.searchTimeout = setTimeout(() => {
            this.fetchBorrowedBooks(newSearchTerm, this.state.borrowDateFilter);
        }, 300);
    }

    handleBorrowDateFilterChange(event) {
        const newBorrowDate = event.target.value;
        this.setState({ borrowDateFilter: newBorrowDate });

        if (this.searchTimeout) {
            clearTimeout(this.searchTimeout);
        }

        this.searchTimeout = setTimeout(() => {
            this.fetchBorrowedBooks(this.state.generalSearchTerm, newBorrowDate);
        }, 300);
    }

    handleKeyPress(event) {
        if (event.key === 'Enter') {
            event.preventDefault();
            if (this.searchTimeout) {
                clearTimeout(this.searchTimeout);
            }
            this.fetchBorrowedBooks(this.state.generalSearchTerm, this.state.borrowDateFilter);
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        }
    }

    async remove(id) {
        try {
            await fetch(`/borrowed-books/${id}`, {
                method: 'DELETE',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });
            this.fetchBorrowedBooks(this.state.generalSearchTerm, this.state.borrowDateFilter);
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        } catch (error) {
            console.error("Error deleting borrowed book record:", error);
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        }
    }

    render() {
        const { borrowedBooks, isLoading, generalSearchTerm, borrowDateFilter } = this.state;

        if (isLoading && borrowedBooks.length === 0) {
            return (
                <div>
                    <AppNavbar />
                    <Container fluid className="text-center py-5">
                        <p className="display-4">Loading Borrowed Books...</p>
                    </Container>
                </div>
            );
        }

        const borrowedBookList = borrowedBooks.map(borrowedBook => {
            const formattedBorrowDate = borrowedBook.borrowDate
                ? new Date(borrowedBook.borrowDate).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
                : 'N/A';
            const formattedReturnDate = borrowedBook.returnDate
                ? new Date(borrowedBook.returnDate).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })
                : 'Not Returned';

            return (
                <tr key={borrowedBook.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{borrowedBook.bookTitle || 'N/A'}</td>
                    <td>{borrowedBook.bookAuthorName || 'N/A'}</td>
                    <td>{borrowedBook.memberName || 'N/A'}</td>
                    <td>{formattedBorrowDate}</td>
                    <td>{formattedReturnDate}</td>
                    <td>
                        <ButtonGroup>
                            <Button size="sm" color="primary" tag={Link} to={"/borrowed-books/" + borrowedBook.id}>Edit</Button>
                            <Button size="sm" color="danger" onClick={() => this.remove(borrowedBook.id)}>Delete</Button>
                        </ButtonGroup>
                    </td>
                </tr>
            );
        });

        return (
            <div>
                <AppNavbar/>
                <Container fluid className="mt-4">
                    <div className="d-flex justify-content-between align-items-center mb-4">
                        <h3 className="mb-0">Borrowed Books</h3>
                        <Button color="success" tag={Link} to="/borrowed-books/new">Borrow New Book</Button>
                    </div>

                    {/* Search Filters */}
                    <Row className="mb-4">
                        <Col md={6}>
                            <FormGroup>
                                <Label for="generalSearchTerm">Search by Member, Book Title, or Author:</Label>
                                <Input
                                    type="text"
                                    name="generalSearchTerm"
                                    id="generalSearchTerm"
                                    placeholder="Type to search..."
                                    value={generalSearchTerm}
                                    onChange={this.handleGeneralSearchChange}
                                    onKeyPress={this.handleKeyPress}
                                    className="form-control"
                                    innerRef={this.searchInputRef}
                                />
                            </FormGroup>
                        </Col>
                        <Col md={6}>
                            <FormGroup>
                                <Label for="borrowDateFilter">Filter by Borrow Date:</Label>
                                <Input
                                    type="date"
                                    name="borrowDateFilter"
                                    id="borrowDateFilter"
                                    value={borrowDateFilter}
                                    onChange={this.handleBorrowDateFilterChange}
                                    className="form-control"
                                    innerRef={this.borrowDateInputRef}
                                />
                            </FormGroup>
                        </Col>
                    </Row>


                    {borrowedBooks.length === 0 && !isLoading ? (
                        <p className="text-muted text-center">No borrowed books found matching your criteria. Click "Borrow New Book" to get started!</p>
                    ) : (
                        <Table responsive hover striped className="shadow-sm">
                            <thead className="bg-dark text-white">
                            <tr>
                                <th width="20%">Book Title</th>
                                <th width="15%">Author</th>
                                <th width="20%">Borrowed By</th>
                                <th width="15%">Borrow Date</th>
                                <th width="15%">Return Date</th>
                                <th width="15%">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {borrowedBookList}
                            </tbody>
                        </Table>
                    )}
                </Container>
            </div>
        );
    }
}
export default BorrowedBookList;