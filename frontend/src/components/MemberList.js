import React, { Component } from 'react';
import {
    Button,
    ButtonGroup,
    Container,
    Table,
    Input,
    FormGroup,
    Label,
    Collapse,
    ListGroup,
    ListGroupItem
} from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';
import { Link } from 'react-router-dom';

class MemberList extends Component {

    searchTimeout = null;
    searchInputRef = React.createRef();

    constructor(props) {
        super(props);
        this.state = {
            members: [],
            isLoading: true,
            searchTerm: '',
            expandedMemberId: null,
            borrowedBooksCache: {}
        };
        this.remove = this.remove.bind(this);
        this.fetchMembers = this.fetchMembers.bind(this);
        this.handleSearchChange = this.handleSearchChange.bind(this);
        this.handleKeyPress = this.handleKeyPress.bind(this);
        this.toggleAccordion = this.toggleAccordion.bind(this);
    }

    componentDidMount() {
        this.fetchMembers(this.state.searchTerm);
        if (this.searchInputRef.current) {
            this.searchInputRef.current.focus();
        }
    }

    componentWillUnmount() {
        if (this.searchTimeout) {
            clearTimeout(this.searchTimeout);
        }
    }

    async fetchMembers(searchName = '') {
        this.setState({ isLoading: true });

        const url = `/members` + (searchName ? `?searchTerm=${encodeURIComponent(searchName)}` : '');

        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            this.setState({ members: data, isLoading: false, expandedMemberId: null }); // Collapse all on new search
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }
        } catch (error) {
            console.error("Error fetching members:", error);
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
            this.fetchMembers(newSearchTerm);
        }, 1000);
    }

    handleKeyPress(event) {
        if (event.key === 'Enter') {
            event.preventDefault();
            if (this.searchTimeout) {
                clearTimeout(this.searchTimeout);
            }
            this.fetchMembers(this.state.searchTerm);
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }
        }
    }

    async remove(id) {
        try {
            await fetch(`/members/${id}`, {
                method: 'DELETE',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                }
            });
            this.fetchMembers(this.state.searchTerm);
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }
        } catch (error) {
            console.error("Error deleting member:", error);
            if (this.searchInputRef.current) {
                this.searchInputRef.current.focus();
            }
        }
    }

    async toggleAccordion(memberId) {
        const { expandedMemberId, borrowedBooksCache } = this.state;

        if (expandedMemberId === memberId) {
            this.setState({ expandedMemberId: null });
        } else {
            this.setState({ isLoading: true });
            if (!borrowedBooksCache[memberId]) {
                try {
                    const booksResponse = await fetch(`/members/${memberId}/borrowedBooks`);
                    if (booksResponse.ok) {
                        const booksData = await booksResponse.json();
                        this.setState(prevState => ({
                            borrowedBooksCache: {
                                ...prevState.borrowedBooksCache,
                                [memberId]: booksData
                            },
                            expandedMemberId: memberId,
                            isLoading: false
                        }));
                    } else if (booksResponse.status === 204) { // No content
                        this.setState(prevState => ({
                            borrowedBooksCache: {
                                ...prevState.borrowedBooksCache,
                                [memberId]: []
                            },
                            expandedMemberId: memberId,
                            isLoading: false
                        }));
                    } else {
                        console.error("Failed to fetch borrowed books:", booksResponse.status);
                        this.setState({ expandedMemberId: memberId, isLoading: false });
                    }
                } catch (error) {
                    console.error("Error fetching borrowed books:", error);
                    this.setState({ expandedMemberId: memberId, isLoading: false });
                }
            } else {
                // If books are already in cache, just expand
                this.setState({ expandedMemberId: memberId, isLoading: false });
            }
        }
    }

    render() {
        const { members, isLoading, searchTerm, expandedMemberId, borrowedBooksCache } = this.state;

        if (isLoading && members.length === 0) {
            return (
                <div>
                    <AppNavbar />
                    <Container fluid className="text-center py-5">
                        <p className="display-4">Loading Members...</p>
                        [Image of a loading spinner]
                    </Container>
                </div>
            );
        }

        const memberList = members.map(member => {
            let formattedDateOfBirth = 'N/A';
            if (member.dateOfBirth) {
                try {
                    const dateObject = new Date(member.dateOfBirth);
                    formattedDateOfBirth = dateObject.toLocaleDateString(undefined, {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric'
                    });
                } catch (e) {
                    console.error("Error parsing dateOfBirth:", member.dateOfBirth, e);
                    formattedDateOfBirth = 'Invalid Date';
                }
            }

            const isExpanded = expandedMemberId === member.id;
            const currentBorrowedBooks = borrowedBooksCache[member.id] || [];

            return (
                <React.Fragment key={member.id}>
                    <tr onClick={() => this.toggleAccordion(member.id)} style={{ cursor: 'pointer' }}>
                        <td style={{ whiteSpace: 'nowrap' }}>{member.name}</td>
                        <td>{formattedDateOfBirth}</td>
                        <td>{member.email}</td>
                        <td>{member.phone}</td>
                        <td>{member.isActive ? 'Yes' : 'No'}</td>
                        <td>
                            <ButtonGroup>
                                <Button size="sm" color="primary" tag={Link} to={"/members/" + member.id}>Edit</Button>
                                <Button size="sm" color="danger" onClick={(e) => { e.stopPropagation(); this.remove(member.id); }}>Delete</Button>
                            </ButtonGroup>
                        </td>
                    </tr>
                    {/* Accordion Row */}
                    <tr>
                        <td colSpan="6" className="p-0 border-0">
                            <Collapse isOpen={isExpanded}>
                                <div className="p-3 bg-light border-top">
                                    <h6>Borrowed Books:</h6>
                                    {isLoading && isExpanded ? (
                                        <p className="text-muted">Loading books...</p>
                                    ) : (
                                        currentBorrowedBooks.length > 0 ? (
                                            <ListGroup flush>
                                                {currentBorrowedBooks.map(book => (
                                                    <ListGroupItem key={book.id}>
                                                        <strong>{book.title}</strong> (Published: {new Date(book.publishedDate).toLocaleDateString()})
                                                    </ListGroupItem>
                                                ))}
                                            </ListGroup>
                                        ) : (
                                            <p className="text-muted">No borrowed books.</p>
                                        )
                                    )}
                                </div>
                            </Collapse>
                        </td>
                    </tr>
                </React.Fragment>
            );
        });

        return (
            <div>
                <AppNavbar/>
                <Container fluid className="mt-4">
                    <div className="d-flex justify-content-between align-items-center mb-4">
                        <h3 className="mb-0">Members</h3>
                        <Button color="success" tag={Link} to="/members/new">Add Member</Button>
                    </div>

                    {/* Search Bar */}
                    <FormGroup className="mb-4">
                        <Label for="searchTerm">Search by Name, Email, or Phone:</Label>
                        <Input
                            type="text"
                            name="searchTerm"
                            id="searchTerm"
                            placeholder="Type to search members..."
                            value={searchTerm}
                            onChange={this.handleSearchChange}
                            onKeyPress={this.handleKeyPress}
                            className="form-control"
                            innerRef={this.searchInputRef}
                        />
                    </FormGroup>

                    {members.length === 0 && !isLoading ? (
                        <p className="text-muted text-center">No members found matching your criteria. Click "Add Member" to get started!</p>
                    ) : (
                        <Table responsive hover striped className="shadow-sm">
                            <thead className="bg-dark text-white">
                            <tr>
                                <th width="20%">Name</th>
                                <th width="15%">Date of Birth</th>
                                <th width="25%">Email</th>
                                <th width="15%">Phone</th>
                                <th width="10%">Active</th>
                                <th width="15%">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {memberList}
                            </tbody>
                        </Table>
                    )}
                </Container>
            </div>
        );
    }
}
export default MemberList;