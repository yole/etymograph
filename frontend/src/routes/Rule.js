import {useLoaderData} from "react-router";

export async function loader({params}) {
    return fetch(`http://localhost:8080/rule/${params.lang}/${params.id}`, { headers: { 'Accept': 'application/json'} })
}

export default function Rule() {
    const rule = useLoaderData()

    return <>
        {rule.branches.map(b => <>
            <div>When:</div>
            <ul>
                {b.conditions.map(c => <li>{c}</li>)}
            </ul>
            <div>Then:</div>
            <ul>
                {b.instructions.map(i => <li>{i}</li>)}
            </ul>
        </>)}
    </>
}
