import React, { Component } from 'react';
import { Button, ButtonGroup, Container, Table } from 'reactstrap';
import AppNavbar from '../fragments/AppNavbar';
import { Link } from 'react-router-dom';

class AuthorList extends Component {

    constructor(props) {
        super(props);
        this.state = {authors: []};
        this.remove = this.remove.bind(this);
    }

    componentDidMount() {
        fetch('/authors')
            .then(response => response.json())
            .then(data => this.setState({authors: data}));
    }

    async remove(id) {
        await fetch(`/authors/${id}`, {
            method: 'DELETE',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            }
        }).then(() => {
            let updatedAuthors = [...this.state.authors].filter(i => i.id !== id);
            this.setState({authors: updatedAuthors});
        });
    }

    render() {
        const {authors, isLoading} = this.state;

        if (isLoading) {
            return <p>Loading...</p>;
        }

        const authorList = authors.map(author => {
            return <tr key={author.id}>
                <td style={{whiteSpace: 'nowrap'}}>{author.name}</td>
                <td>{author.dateOfBirth}</td>
                <td>
                    <ButtonGroup>
                        <Button size="sm" color="primary" tag={Link} to={"/authors/" + author.id}>Edit</Button>
                        <Button size="sm" color="danger" onClick={() => this.remove(author.id)}>Delete</Button>
                    </ButtonGroup>
                </td>
            </tr>
        });

        return (
            <div>
                <AppNavbar/>
                <Container fluid>
                    <div className="float-right">
                        <Button color="success" tag={Link} to="/authors/new">Add Author</Button>
                    </div>
                    <h3>Authors</h3>
                    <Table className="mt-4">
                        <thead>
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
                </Container>
            </div>
        );
    }
}
export default AuthorList;