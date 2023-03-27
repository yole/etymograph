import {useLoaderData, useParams} from "react-router";
import {Link} from "react-router-dom";
import {useEffect} from "react";

export async function loader({params}) {
    return fetch(`${process.env.REACT_APP_BACKEND_URL}rules/${params.langId}`, { headers: { 'Accept': 'application/json'} })
}

export default function RuleList() {
    const ruleList = useLoaderData()
    const params = useParams()
    useEffect(() => { document.title = "Etymograph : Rules" })

    return <>
    <h2>
        <small>
            <Link to={`/`}>Etymograph</Link> >{' '}
            <Link to={`/language/${params.langId}`}>{ruleList.toLangFullName}</Link> > </small>
        Rules
    </h2>
        {ruleList.ruleGroups.map(g => <>
            <h2>{g.groupName}</h2>
            <ul>
                {g.rules.map(r => <li key={r.id}><Link to={`/rule/${r.id}`}>{r.name}</Link>{r.summaryText.length > 0 ? ": " + r.summaryText : ""}</li>)}
            </ul>
        </>)}
        <Link to="/rules/new">Add rule</Link>
    </>
}
