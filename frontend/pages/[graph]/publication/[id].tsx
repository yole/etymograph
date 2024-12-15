import {fetchBackend, fetchPathsForAllGraphs} from "@/api";
import {useContext} from "react";
import PublicationForm from "@/forms/PublicationForm";
import Breadcrumbs from "@/components/Breadcrumbs";
import EtymographFormView, {View} from "@/components/EtymographFormView";
import {GraphContext} from "@/components/Contexts";
import { PublicationData } from "@/model";

export const config = {
    unstable_runtimeJS: true
}

export async function getStaticProps(context) {
    return fetchBackend(context.params.graph, `publication/${context.params.id}`)
}

export async function getStaticPaths() {
    return fetchPathsForAllGraphs("publications", (p) => ({id: p.id.toString()}))
}

export default function Publication(props) {
    const publication = props.loaderData as PublicationData;
    const graph = useContext(GraphContext)

    return <>
        <Breadcrumbs steps={[{title: "Bibliography", url: `/${graph}/publications`}]} title={publication.refId} />

        <EtymographFormView>
            <View>
                <p>{publication.name}</p>
            </View>
            <PublicationForm
                updateId={publication.id}
                defaultValues={{name: publication.name, refId: publication.refId}}
            />
        </EtymographFormView>
    </>
}
