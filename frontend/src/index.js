import React from 'react';
import ReactDOM from 'react-dom/client';
import {
    createBrowserRouter,
    RouterProvider,
    Route,
} from "react-router-dom";
import './index.css';
import CorpusIndex, { loader as corpusIndexLoader } from './CorpusIndex'
import CorpusLangIndex, { loader as corpusLangIndexLoader } from "./CorpusLangIndex";
import CorpusText, { loader as corpusTextLoader } from "./CorpusText";
import Word, { loader as wordLoader } from "./Word";

const router = createBrowserRouter([
    {
        path: '/',
        element: <CorpusIndex/>,
        loader: corpusIndexLoader
    },
    {
        path: '/corpus/:langId',
        element: <CorpusLangIndex/>,
        loader: corpusLangIndexLoader
    },
    {
        path: '/corpus/text/:id',
        element: <CorpusText/>,
        loader: corpusTextLoader
    },
    {
        path: '/word/:lang/:text',
        element: <Word/>,
        loader: wordLoader
    }
])

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <RouterProvider router={router}/>
  </React.StrictMode>
);
