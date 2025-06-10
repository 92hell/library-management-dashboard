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

class BookEdit extends Component {

    emptyItem = {
        title: '',
        publishedDate: '',
        publisherName: '',
        author: null,
        categories: ''
    };

    constructor(props) {
        super(props);
        this.state = {
            item: this.emptyItem,
            availableAuthors: [],
            availableCategories: [],
            isLoadingAuthors: true,
            isLoadingCategories: true
        };
        this.handleChange = this.handleChange.bind(this);
        this.handleAuthorSelectChange = this.handleAuthorSelectChange.bind(this);
        this.handleCategorySelectChange = this.handleCategorySelectChange.bind(this);
        this.handleSubmit = this.handleSubmit.bind(this);
    }

    async componentDidMount() {
        const { id } = this.props.match.params;

        try {
            const authorsResponse = await fetch('/authors/active');
            if (authorsResponse.ok) {
                const authorsData = await authorsResponse.json();
                this.setState({ availableAuthors: authorsData, isLoadingAuthors: false });
            } else {
                console.error("Failed to fetch authors:", authorsResponse.status);
                this.setState({ isLoadingAuthors: false });
            }
        } catch (error) {
            console.error("Error fetching authors:", error);
            this.setState({ isLoadingAuthors: false });
        }

        try {
            const categoriesResponse = await fetch('/books/categories');
            if (categoriesResponse.ok) {
                const categoriesData = await categoriesResponse.json();
                this.setState({ availableCategories: categoriesData, isLoadingCategories: false });
            } else {
                console.error("Failed to fetch categories:", categoriesResponse.status);
                this.setState({ isLoadingCategories: false });
            }
        } catch (error) {
            console.error("Error fetching categories:", error);
            this.setState({ isLoadingCategories: false });
        }


        if (id !== 'new') {
            try {
                const bookResponse = await fetch(`/books/${id}`);
                if (!bookResponse.ok) {
                    throw new Error(`Failed to fetch book: ${bookResponse.status}`);
                }
                const bookData = await bookResponse.json();

                if (bookData.publishedDate) {
                    const date = new Date(bookData.publishedDate);
                    if (!isNaN(date.getTime())) {
                        const year = date.getFullYear();
                        const month = String(date.getMonth() + 1).padStart(2, '0');
                        const day = String(date.getDate()).padStart(2, '0');
                        bookData.publishedDate = `${year}-${month}-${day}`;
                    } else {
                        console.warn("Received invalid publishedDate from backend:", bookData.publishedDate);
                        bookData.publishedDate = '';
                    }
                }

                if (!bookData.categories) {
                    bookData.categories = '';
                }

                this.setState({ item: bookData });
            } catch (error) {
                console.error("Error fetching book:", error);
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

    handleAuthorSelectChange(event) {
        const selectedAuthorId = event.target.value;
        const selectedAuthor = this.state.availableAuthors.find(author =>
            author.id === parseInt(selectedAuthorId)
        );
        let item = { ...this.state.item };
        item.author = selectedAuthor;
        this.setState({ item });
    }

    handleCategorySelectChange(event) {
        const selectedCategory = event.target.value;
        let item = { ...this.state.item };
        item.categories = selectedCategory;
        this.setState({ item });
    }

    async handleSubmit(event) {
        event.preventDefault();
        const { item } = this.state;

        if (item.author === null || item.author.id === undefined) {
            alert('Please select an author.');
            return;
        }

        try {
            await fetch('/books' + (item.id ? '/' + item.id : ''), {
                method: (item.id) ? 'PUT' : 'POST',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(item),
            });
            this.props.history.push('/books');
        } catch (error) {
            console.error("Error saving book:", error);
        }
    }

    render() {
        const { item, availableAuthors, availableCategories, isLoadingAuthors, isLoadingCategories } = this.state;
        const title = <h2>{item.id ? 'Edit Book' : 'Add Book'}</h2>;

        return (
            <div>
                <AppNavbar/>
                <Container className="my-4">
                    {title}
                    <Form onSubmit={this.handleSubmit} className="p-4 border rounded shadow-sm">
                        <FormGroup>
                            <Label for="title">Title</Label>
                            <Input type="text" name="title" id="title" value={item.title || ''}
                                   onChange={this.handleChange} autoComplete="title" required />
                        </FormGroup>
                        <FormGroup>
                            <Label for="publisherName">Publisher Name</Label>
                            <Input type="text" name="publisherName" id="publisherName" value={item.publisherName || ''}
                                   onChange={this.handleChange} autoComplete="publisherName" />
                        </FormGroup>
                        <FormGroup>
                            <Label for="publishedDate">Published Date</Label>
                            <Input type="date" name="publishedDate" id="publishedDate" value={item.publishedDate || ''}
                                   onChange={this.handleChange} required />
                        </FormGroup>
                        <FormGroup>
                            <Label for="author">Author</Label>
                            {isLoadingAuthors ? (
                                <p className="text-muted">Loading authors...</p>
                            ) : (
                                <Input type="select" name="author" id="author"
                                       value={item.author ? item.author.id : ''}
                                       onChange={this.handleAuthorSelectChange} required>
                                    <option value="">Select an Author</option>
                                    {availableAuthors.map(author => (
                                        <option key={author.id} value={author.id}>{author.name}</option>
                                    ))}
                                </Input>
                            )}
                        </FormGroup>
                        <FormGroup>
                            <Label for="categories">Category</Label>
                            {isLoadingCategories ? (
                                <p className="text-muted">Loading categories...</p>
                            ) : (
                                <Input type="select" name="categories" id="categories"
                                       value={item.categories || ''}
                                       onChange={this.handleCategorySelectChange}
                                       required>
                                    <option value="">Select a Category</option>
                                    {availableCategories.map(category => (
                                        <option key={category} value={category}>{category}</option>
                                    ))}
                                </Input>
                            )}
                        </FormGroup>
                        <FormGroup className="mt-4">
                            <Button color="primary" type="submit">Save</Button>{' '}
                            <Button color="secondary" tag={Link} to="/books">Cancel</Button>
                        </FormGroup>
                    </Form>
                </Container>
            </div>
        );
    }
}
export default withRouter(BookEdit);