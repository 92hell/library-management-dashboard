import React, { Component } from 'react';
import { Button, ButtonGroup, Container, Table, Input, FormGroup, Label } from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';
import { Link } from 'react-router-dom';

class AuthorList extends Component {

    searchTimeout = null;
    searchInputRef = React.createRef();

    constructor(props) {
        super(props);
        this.state = {
            authors: [],
            isLoading: true,
            searchTerm: ''
        };
        this.remove = this.remove.bind(this);
        this.fetchAuthors = this.fetchAuthors.bind(this);
        this.handleSearchChange = this.handleSearchChange.bind(this);
        this.handleKeyPress = this.handleKeyPress.bind(this);
    }

    componentDidMount() {
        this.fetchAuthors(this.state.searchTerm);
        if (this.searchInputRef.current) {
            this.searchInputRef.current.focus();
        }
    }

    componentWillUnmount() {
        if (this.searchTimeout) {
            clearTimeout(this.searchTimeout);
        }
    }

    async fetchAuthors(searchName = '') {
        this.setState({ isLoading: true });

        const url = `/authors` + (searchName ? `?name=${encodeURIComponent(searchName)}` : '');

        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            this.setState({ authors: data, isLoading: false });

            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }

        } catch (error) {
            console.error("Error fetching authors:", error);
            this.setState({ isLoading: false });
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
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
            this.fetchAuthors(newSearchTerm);
        }, 1000);
    }

    handleKeyPress(event) {
        if (event.key === 'Enter') {
            event.preventDefault();
            if (this.searchTimeout) {
                clearTimeout(this.searchTimeout);
            }
            this.fetchAuthors(this.state.searchTerm);
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }
        }
    }

    async remove(id) {
        try {
            await fetch(`/authors/${id}`, {
                method: 'DELETE',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });
            let updatedAuthors = [...this.state.authors].filter(i => i.id !== id);
            this.setState({ authors: updatedAuthors });
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }
        } catch (error) {
            console.error("Error deleting author:", error);
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }
        }
    }

    render() {
        const { authors, isLoading, searchTerm } = this.state;

        if (isLoading) {
            return (
                <div>
                    <AppNavbar />
                    <Container fluid className="text-center py-5">
                        <p className="display-4">Loading Authors...</p>
                        [Image of a loading spinner]
                    </Container>
                </div>
            );
        }

        const authorList = authors.map(author => {
            let formattedDateOfBirth = 'N/A';
            if (author.dateOfBirth) {
                try {
                    const dateObject = new Date(author.dateOfBirth);
                    formattedDateOfBirth = dateObject.toLocaleDateString(undefined, {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric'
                    });
                } catch (e) {
                    console.error("Error parsing dateOfBirth:", author.dateOfBirth, e);
                    formattedDateOfBirth = 'Invalid Date';
                }
            }

            return (
                <tr key={author.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{author.name}</td>
                    <td>{formattedDateOfBirth}</td>
                    <td>
                        <ButtonGroup>
                            <Button size="sm" color="primary" tag={Link} to={"/authors/" + author.id}>Edit</Button>
                            <Button size="sm" color="danger" onClick={() => this.remove(author.id)}>Delete</Button>
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
                        <h3 className="mb-0">Authors</h3>
                        <Button color="success" tag={Link} to="/authors/new">Add Author</Button>
                    </div>

                    {/* Search Bar */}
                    <FormGroup className="mb-4">
                        <Label for="searchName">Search by Name:</Label>
                        <Input
                            type="text"
                            name="searchName"
                            id="searchName"
                            placeholder="Type to search authors..."
                            value={searchTerm}
                            onChange={this.handleSearchChange}
                            onKeyPress={this.handleKeyPress}
                            className="form-control"
                            innerRef={this.searchInputRef}
                        />
                    </FormGroup>

                    {authors.length === 0 && !isLoading ? (
                        <p className="text-muted text-center">No authors found matching your criteria. Click "Add Author" to get started!</p>
                    ) : (
                        <Table responsive hover striped className="shadow-sm">
                            <thead className="bg-dark text-white">
                            <tr>
                                <th width="30%">Name</th>
                                <th width="30%">Date of Birth</th>
                                <th width="40%">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {authorList}
                            </tbody>
                        </Table>
                    )}
                </Container>
            </div>
        );
    }
}
export default AuthorList;