import React, { Component } from 'react';
import { Button, ButtonGroup, Container, Table, Input, FormGroup, Label } from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';
import { Link } from 'react-router-dom';

class BookList extends Component {

    searchTimeout = null;
    searchInputRef = React.createRef();

    constructor(props) {
        super(props);
        this.state = {
            books: [],
            isLoading: true,
            searchTerm: ''
        };
        this.remove = this.remove.bind(this);
        this.fetchBooks = this.fetchBooks.bind(this);
        this.handleSearchChange = this.handleSearchChange.bind(this);
        this.handleKeyPress = this.handleKeyPress.bind(this);
    }

    componentDidMount() {
        this.fetchBooks(this.state.searchTerm);
        if (this.searchInputRef.current) {
            this.searchInputRef.focus();
        }
    }

    componentWillUnmount() {
        if (this.searchTimeout) {
            clearTimeout(this.searchTimeout);
        }
    }

    async fetchBooks(searchName = '') {
        this.setState({ isLoading: true });

        const url = `/books` + (searchName ? `?searchTerm=${encodeURIComponent(searchName)}` : '');

        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            console.log("data:", data);
            this.setState({ books: data, isLoading: false });
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        } catch (error) {
            console.error("Error fetching books:", error);
            this.setState({ isLoading: false });
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        }
    }

    handleSearchChange(event) {
        const newSearchTerm = event.target.value;
        this.setState({ searchTerm: newSearchTerm });

        if (this.searchTimeout) {
            clearTimeout(this.searchTimeout);
        }

        this.searchTimeout = setTimeout(() => {
            this.fetchBooks(newSearchTerm);
        }, 1000);
    }

    handleKeyPress(event) {
        if (event.key === 'Enter') {
            event.preventDefault();
            if (this.searchTimeout) {
                clearTimeout(this.searchTimeout);
            }
            this.fetchBooks(this.state.searchTerm);
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        }
    }

    async remove(id) {
        try {
            await fetch(`/books/${id}`, {
                method: 'DELETE',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });

            this.fetchBooks(this.state.searchTerm);
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        } catch (error) {
            console.error("Error deleting book:", error);
            if (this.searchInputRef.current) {
                this.searchInputRef.focus();
            }
        }
    }

    render() {
        const { books, isLoading, searchTerm } = this.state;

        if (isLoading && books.length === 0) {
            return (
                <div>
                    <AppNavbar />
                    <Container fluid className="text-center py-5">
                        <p className="display-4">Loading Books...</p>

                    </Container>
                </div>
            );
        }

        const bookList = books.map(book => {
            let formattedPublishedDate = 'N/A';
            if (book.publishedDate) {
                try {
                    const dateObject = new Date(book.publishedDate);
                    formattedPublishedDate = dateObject.toLocaleDateString(undefined, {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric'
                    });
                } catch (e) {
                    console.error("Error parsing publishedDate:", book.publishedDate, e);
                    formattedPublishedDate = 'Invalid Date';
                }
            }

            const displayCategories = book.categories || 'N/A';

            return (
                <tr key={book.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{book.title}</td>
                    <td>{book.author ? book.author.name : 'N/A'}</td>
                    <td>{formattedPublishedDate}</td>
                    <td>{book.publisherName}</td>
                    <td>{displayCategories}</td>
                    <td>
                        <ButtonGroup>
                            <Button size="sm" color="primary" tag={Link} to={"/books/" + book.id}>Edit</Button>
                            <Button size="sm" color="danger" onClick={() => this.remove(book.id)}>Delete</Button>
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
                        <h3 className="mb-0">Books</h3>
                        <Button color="success" tag={Link} to="/books/new">Add Book</Button>
                    </div>

                    {/* Search Bar */}
                    <FormGroup className="mb-4">
                        <Label for="searchTerm">Search by Title, Publisher, Category, or Author:</Label>
                        <Input
                            type="text"
                            name="searchTerm"
                            id="searchTerm"
                            placeholder="Type to search books..."
                            value={searchTerm}
                            onChange={this.handleSearchChange}
                            onKeyPress={this.handleKeyPress}
                            className="form-control"
                            innerRef={this.searchInputRef}
                        />
                    </FormGroup>

                    {books.length === 0 && !isLoading ? (
                        <p className="text-muted text-center">No books found matching your criteria. Click "Add Book" to get started!</p>
                    ) : (
                        <Table responsive hover striped className="shadow-sm">
                            <thead className="bg-dark text-white">
                            <tr>
                                <th width="20%">Title</th>
                                <th width="15%">Author</th>
                                <th width="15%">Published Date</th>
                                <th width="20%">Publisher</th>
                                <th width="15%">Categories</th>
                                <th width="15%">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {bookList}
                            </tbody>
                        </Table>
                    )}
                </Container>
            </div>
        );
    }
}
export default BookList;