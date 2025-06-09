import React, { Component } from 'react';
import '../App.css';
import AppNavbar from '../fragments/AppNavbar';
import { Link } from 'react-router-dom';
import { Button, Container } from 'reactstrap';

class Home extends Component {
    render() {
        return (
            <div>
                <AppNavbar/>
                <Container fluid className="my-5 p-5 bg-light border rounded shadow-sm text-center">
                    <h1 className="display-4 mb-4">Welcome to My Library Dashboard!</h1>
                    <p className="lead">
                        This is my mock website for a job application test.
                        This application works as a dashboard or backoffice site for library workers,
                        allowing them to track books, authors, members, and borrowed books in their library.
                    </p>
                    <hr className="my-4" />
                    <p className="mb-4">
                        This is my first time working with React, and I've put a lot of effort into it. I hope you like it! 😊
                    </p>
                </Container>
            </div>
        );
    }
}

export default Home;